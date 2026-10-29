package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.neo4j.CypherCatalog;
import com.db2.proyectofinaldb2.repository.EvaluationRepository;
import com.db2.proyectofinaldb2.repository.EvaluationRepository.InsertResult;
import com.db2.proyectofinaldb2.util.CsvSupport;
import com.opencsv.CSVReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EtlService {

    private static final Logger log = LoggerFactory.getLogger(EtlService.class);

    private final EvaluationRepository repository;
    private final TransformationService transformationService;
    private final Driver driver;
    private final int batchSize;

    public EtlService(EvaluationRepository repository,
                      TransformationService transformationService,
                      Driver driver,
                      @Value("${app.etl.batch-size}") int batchSize) {
        this.repository = repository;
        this.transformationService = transformationService;
        this.driver = driver;
        if (batchSize < 1 || batchSize > 10000) {
            throw new IllegalArgumentException("El tamaño de lote ETL debe estar entre 1 y 10000");
        }
        this.batchSize = batchSize;
    }

    public void process(CargaJob job) {
        boolean anyLoaded = false;
        for (CargaJob.FileJob file : job.getArchivos()) {
            long start = System.currentTimeMillis();
            try {
                int processed = processFile(job, file);
                anyLoaded = anyLoaded || processed > 0;
                file.setEstado(file.getRechazados().get() > 0
                        ? CargaJob.Estado.EXITOSA_CON_RECHAZOS
                        : CargaJob.Estado.EXITOSA);
            } catch (Exception exception) {
                log.error("Error al procesar el archivo {}", file.getNombre(), exception);
                file.setEstado(CargaJob.Estado.ERROR);
                String message = exception instanceof IllegalArgumentException ? exception.getMessage()
                        : "No se completó la lectura o persistencia. Puede haber datos parciales; revise las métricas y reintente el archivo corregido cuando las bases estén disponibles";
                file.setDetalleError(message);
                file.addError(0, message, null);
            } finally {
                anyLoaded = anyLoaded || file.getProcesados().get() > 0;
                file.setDuracionMs(System.currentTimeMillis() - start);
            }
        }

        if (anyLoaded) {
            try {
                repository.rebuildAspirantes();
            } catch (Exception exception) {
                log.error("No se pudo reconstruir la colección de aspirantes", exception);
                job.fail("Los datos se cargaron parcialmente, pero no se pudo generar la colección de aspirantes");
                return;
            }
        }
        job.finish();
    }

    private int processFile(CargaJob job, CargaJob.FileJob file) throws Exception {
        Path path = file.getRuta();
        char delimiter = CsvSupport.detectDelimiter(path);
        List<Document> mongoBatch = new ArrayList<>(batchSize);
        List<Map<String, Object>> graphBatch = new ArrayList<>(batchSize);
        List<Document> rejectedBatch = new ArrayList<>();

        try (CSVReader reader = CsvSupport.reader(path, delimiter)) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new IllegalArgumentException("El archivo está vacío");
            }
            Map<String, Integer> indexes = transformationService.mapHeaders(header);
            List<String> missing = TransformationService.REQUIRED_FIELDS.stream()
                    .filter(field -> !indexes.containsKey(field))
                    .sorted()
                    .toList();
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Faltan columnas obligatorias en el archivo: " + String.join(", ", missing));
            }

            String[] row;
            while (true) {
                int line = Math.toIntExact(reader.getLinesRead() + 1);
                row = reader.readNext();
                if (row == null) break;
                if (row.length == 0 || Arrays.stream(row).allMatch(value -> value == null || value.isBlank())) {
                    continue;
                }
                file.getLeidos().incrementAndGet();
                try {
                    if (row.length != header.length) {
                        throw new TransformationService.RowValidationException("Cantidad de columnas incorrecta: " + row.length + "; esperadas " + header.length);
                    }
                    TransformationService.TransformedRow transformed =
                            transformationService.transform(row, indexes, file.getNombre(), line);
                    mongoBatch.add(transformed.mongo());
                    graphBatch.add(transformed.graph());
                } catch (TransformationService.RowValidationException exception) {
                    file.getRechazados().incrementAndGet();
                    file.addError(line, exception.getMessage(), transformationService.safeRow(row));
                    rejectedBatch.add(rejectedDocument(job, file, line, exception.getMessage(), row));
                }

                if (mongoBatch.size() >= batchSize || rejectedBatch.size() >= batchSize) {
                    flush(job, file, mongoBatch, graphBatch, rejectedBatch);
                }
            }
            if (file.getLeidos().get() == 0) {
                throw new IllegalArgumentException("El archivo no contiene registros de datos");
            }
            flush(job, file, mongoBatch, graphBatch, rejectedBatch);
        }
        return file.getProcesados().get();
    }

    private void flush(CargaJob job, CargaJob.FileJob file,
                       List<Document> mongoBatch,
                       List<Map<String, Object>> graphBatch,
                       List<Document> rejectedBatch) {
        if (!rejectedBatch.isEmpty()) {
            repository.insertRejected(rejectedBatch);
            rejectedBatch.clear();
        }
        if (mongoBatch.isEmpty()) {
            return;
        }

        InsertResult result = repository.insertBatch(mongoBatch);
        file.getProcesados().addAndGet(result.inserted() + result.duplicates());
        file.getDuplicados().addAndGet(result.duplicates());
        if (result.failed() > 0) {
            file.getRechazados().addAndGet(result.failed());
            file.addError(0, "No se pudieron insertar " + result.failed() + " registros en MongoDB", null);
            List<Document> failures = new ArrayList<>();
            for (int index : result.failedIndexes()) {
                Document row = mongoBatch.get(index);
                String cause = "MongoDB rechazó la escritura de esta evaluación";
                int line = row.getInteger("fila_origen", 0);
                file.addError(line, cause, row.getString("hash_fila"));
                failures.add(new Document("carga_id", job.getId()).append("archivo", file.getNombre()).append("fila", line)
                        .append("causa", cause).append("hash_fila", row.getString("hash_fila"))
                        .append("fecha", new Date()));
            }
            repository.insertRejected(failures);
        }

        List<Map<String, Object>> accepted = new ArrayList<>();
        for (int i = 0; i < graphBatch.size(); i++) {
            if (!result.failedIndexes().contains(i)) accepted.add(graphBatch.get(i));
        }
        if (result.failed() > 0 && result.failedIndexes().isEmpty()) {
            throw new IllegalStateException("No se pudieron identificar las filas rechazadas por MongoDB");
        }
        loadGraph(accepted);
        file.getSincronizadosNeo4j().addAndGet(accepted.size());
        mongoBatch.clear();
        graphBatch.clear();
    }

    private void loadGraph(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        try (Session session = driver.session()) {
            session.executeWrite(transaction -> {
                transaction.run(CypherCatalog.LOAD_BATCH, Map.of("rows", rows)).consume();
                return null;
            });
        }
    }

    private Document rejectedDocument(CargaJob job,
                                      CargaJob.FileJob file,
                                      int line,
                                      String cause,
                                      String[] row) {
        return new Document("carga_id", job.getId())
                .append("archivo", file.getNombre())
                .append("fila", line)
                .append("causa", cause)
                .append("valor", transformationService.safeRow(row))
                .append("fecha", new Date());
    }
}
