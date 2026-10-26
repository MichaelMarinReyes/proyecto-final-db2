package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.dto.CargaReporteDto;
import com.db2.proyectofinaldb2.exception.ApiException;
import com.db2.proyectofinaldb2.repository.EvaluationRepository;
import com.db2.proyectofinaldb2.util.MongoJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CargaService {

    private static final Logger log = LoggerFactory.getLogger(CargaService.class);

    private final EvaluationRepository repository;
    private final EtlService etlService;
    private final ExecutorService etlExecutor;
    private final Map<String, CargaJob> jobs = new ConcurrentHashMap<>();
    private final String configuredTempDir;

    public CargaService(EvaluationRepository repository,
                        EtlService etlService,
                        ExecutorService etlExecutor,
                        @Value("${app.etl.temp-dir:}") String configuredTempDir) {
        this.repository = repository;
        this.etlService = etlService;
        this.etlExecutor = etlExecutor;
        this.configuredTempDir = configuredTempDir;
    }

    public CargaReporteDto start(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe seleccionar al menos un archivo CSV");
        }

        Path tempDir;
        try {
            if (configuredTempDir == null || configuredTempDir.isBlank()) {
                tempDir = Files.createTempDirectory("cargas-");
            } else {
                Path base = Files.createDirectories(Path.of(configuredTempDir));
                tempDir = Files.createTempDirectory(base, "cargas-");
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo crear el directorio temporal para la carga");
        }

        CargaJob job = new CargaJob("carga-" + UUID.randomUUID());
        try {
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                String originalName = file.getOriginalFilename();
                if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El archivo '" + originalName + "' no es un CSV válido");
                }
                if (file.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "El archivo '" + originalName + "' está vacío");
                }
                Path target = tempDir.resolve(i + "-" + sanitize(originalName));
                file.transferTo(target);
                job.addFile(new CargaJob.FileJob(originalName, target));
            }
        } catch (IOException exception) {
            cleanup(tempDir);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el archivo para su procesamiento");
        } catch (RuntimeException exception) {
            cleanup(tempDir);
            throw exception;
        }

        jobs.put(job.getId(), job);
        repository.ensureIndexes();
        repository.replaceLoad(job.toDocument());

        etlExecutor.submit(() -> {
            try {
                etlService.process(job);
            } catch (Exception exception) {
                log.error("Fallo inesperado en el proceso de carga {}", job.getId(), exception);
                job.fail("Error inesperado durante la carga: " + exception.getMessage());
            } finally {
                repository.replaceLoad(job.toDocument());
                cleanup(tempDir);
            }
        });

        return job.snapshot();
    }

    public Object getJob(String id) {
        CargaJob job = jobs.get(id);
        if (job != null) {
            return job.snapshot();
        }
        var document = repository.findLoad(id);
        if (document == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No se encontró la carga solicitada");
        }
        return MongoJson.toMap(document);
    }

    public List<Map<String, Object>> getJobs() {
        return MongoJson.toList(repository.findLoads(50));
    }

    public List<Map<String, Object>> getRejected(String cargaId, int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El número de página no puede ser negativo");
        }
        int safeSize = Math.min(Math.max(size, 1), 500);
        return MongoJson.toList(repository.findRejected(cargaId, page, safeSize));
    }

    private String sanitize(String name) {
        String clean = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean.isBlank() ? "archivo.csv" : clean;
    }

    private void cleanup(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            List<Path> paths = new ArrayList<>(walk.toList());
            paths.sort(Comparator.reverseOrder());
            for (Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        } catch (IOException exception) {
            log.warn("No se pudo limpiar el directorio temporal {}: {}", directory, exception.getMessage());
        }
    }
}
