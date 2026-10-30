package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.dto.SavedQueryRequest;
import com.db2.proyectofinaldb2.exception.ApiException;
import com.db2.proyectofinaldb2.util.MongoJson;
import com.mongodb.MongoException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.model.UpdateOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.neo4j.driver.Driver;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SavedQueryService {
    private static final Logger log = LoggerFactory.getLogger(SavedQueryService.class);
    private static final String CATALOG = "consultas_personalizadas";
    private final MongoTemplate mongo;
    private final Driver driver;
    private final CustomQueryPolicy policy;
    private final String adminKey;
    private final int maxRows;
    private final int timeout;

    public SavedQueryService(MongoTemplate mongo, Driver driver, CustomQueryPolicy policy,
                             @Value("${app.queries.admin-key:}") String adminKey,
                             @Value("${app.queries.max-rows:200}") int maxRows,
                             @Value("${app.queries.timeout-seconds:10}") int timeout) {
        this.mongo = mongo;
        this.driver = driver;
        this.policy = policy;
        this.adminKey = adminKey;
        this.maxRows = Math.max(1, Math.min(maxRows, 500));
        this.timeout = Math.max(1, Math.min(timeout, 60));
    }

    public void authorize(String key) {
        if (adminKey == null || adminKey.length() < 32) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure una clave administrativa de al menos 32 caracteres en el .env del backend");
        }
        if (key == null || !MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Clave de administración incorrecta");
        }
    }

    public List<Map<String, Object>> list(String engine) {
        return MongoJson.toList(mongo.getCollection(CATALOG)
                .find(new Document("motor", policy.engine(engine))).sort(new Document("creadoEn", -1))
                .limit(200).into(new ArrayList<>()));
    }

    public Map<String, Object> save(SavedQueryRequest request) {
        String engine = policy.engine(request.engine());
        boolean write = policy.writes(engine, request.code());
        if (engine.equals("NEO4J")) write = compileNeo4j(request.code()) || write;
        Document entry = new Document("_id", UUID.randomUUID().toString())
                .append("nombre", request.name().trim())
                .append("descripcion", request.description() == null ? "" : request.description().trim())
                .append("motor", engine).append("codigo", request.code().trim())
                .append("modo", write ? "ESCRITURA" : "LECTURA").append("creadoEn", new Date());
        mongo.getCollection(CATALOG).insertOne(entry);
        return MongoJson.toMap(entry);
    }

    public Map<String, Object> execute(String id, boolean confirmed) {
        Document entry = mongo.getCollection(CATALOG).find(new Document("_id", id)).first();
        if (entry == null) throw new ApiException(HttpStatus.NOT_FOUND, "La consulta guardada no existe");
        String code = entry.getString("codigo");
        String engine = entry.getString("motor");
        boolean write = policy.writes(engine, code);
        if (write && !confirmed) {
            throw new ApiException(HttpStatus.CONFLICT, "Esta consulta modifica datos. Confirme la escritura antes de ejecutarla");
        }
        if (engine.equals("NEO4J")) {
            write = compileNeo4j(code) || write;
            if (write && !confirmed) {
                throw new ApiException(HttpStatus.CONFLICT, "La sentencia Cypher contiene operaciones de escritura; es necesaria la confirmación explícita");
            }
        }
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> result = engine.equals("MONGODB") ? executeMongo(code) : executeNeo4j(code);
            result.put("modo", write ? "ESCRITURA" : "LECTURA");
            result.put("duracionMs", System.currentTimeMillis() - start);
            result.put("limiteFilas", maxRows);
            log.info("Consulta personalizada ejecutada: id={}, motor={}, modo={}", id, engine, result.get("modo"));
            return result;
        } catch (ClientException exception) {
            log.warn("Sentencia Cypher personalizada inválida: id={}, tipo={}", id, exception.code());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Neo4j rechazó la sentencia Cypher. Revise la sintaxis y los tipos de datos");
        } catch (MongoException exception) {
            if (exception.getCode() == 50) {
                throw new ApiException(HttpStatus.REQUEST_TIMEOUT, "La consulta superó el tiempo máximo permitido");
            }
            if (exception instanceof MongoCommandException || exception instanceof MongoWriteException
                    || exception instanceof MongoBulkWriteException) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MongoDB rechazó el comando JSON. Revise operadores, tipos y claves duplicadas");
            }
            throw exception;
        }
    }

    private Map<String, Object> executeMongo(String code) {
        Document command = policy.mongo(code);
        var collection = mongo.getCollection(command.getString("coleccion")).withTimeout(timeout, TimeUnit.SECONDS);
        List<Document> rows = new ArrayList<>();
        Map<String, Object> changes = new LinkedHashMap<>();
        switch (command.getString("operacion")) {
            case "find" -> collection.find(command.get("filtro", Document.class))
                    .maxTime(timeout, TimeUnit.SECONDS).limit(maxRows + 1).into(rows);
            case "aggregate" -> {
                var aggregation = collection.aggregate(command.getList("pipeline", Document.class))
                        .maxTime(timeout, TimeUnit.SECONDS);
                try (var cursor = aggregation.iterator()) {
                    while (cursor.hasNext() && rows.size() <= maxRows) rows.add(cursor.next());
                }
            }
            case "insertMany" -> {
                var inserted = collection.insertMany(command.getList("documentos", Document.class));
                changes.put("insertados", inserted.getInsertedIds().size());
            }
            case "updateMany" -> {
                var updated = collection.updateMany(command.get("filtro", Document.class),
                        command.get("actualizacion", Document.class), new UpdateOptions().upsert(false));
                changes.put("coincidencias", updated.getMatchedCount());
                changes.put("modificados", updated.getModifiedCount());
            }
            case "deleteMany" -> changes.put("eliminados", collection.deleteMany(command.get("filtro", Document.class)).getDeletedCount());
            default -> throw new IllegalStateException("Operación no validada");
        }
        boolean truncated = rows.size() > maxRows;
        return response(MongoJson.toList(rows.subList(0, Math.min(rows.size(), maxRows))), changes, truncated);
    }

    private Map<String, Object> executeNeo4j(String code) {
        try (var session = driver.session()) {
            // An explicit transaction avoids automatic retries of arbitrary, possibly non-idempotent writes.
            try (var transaction = session.beginTransaction(TransactionConfig.builder()
                    .withTimeout(Duration.ofSeconds(timeout)).build())) {
                var result = transaction.run(code);
                List<Map<String, Object>> rows = new ArrayList<>();
                while (result.hasNext() && rows.size() <= maxRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    result.next().asMap().forEach((key, value) -> row.put(key, graphValue(value)));
                    rows.add(row);
                }
                boolean truncated = rows.size() > maxRows;
                var counters = result.consume().counters();
                Map<String, Object> changes = Map.of("nodosCreados", counters.nodesCreated(),
                        "nodosEliminados", counters.nodesDeleted(), "relacionesCreadas", counters.relationshipsCreated(),
                        "relacionesEliminadas", counters.relationshipsDeleted(), "propiedadesActualizadas", counters.propertiesSet());
                transaction.commit();
                return response(rows.subList(0, Math.min(rows.size(), maxRows)), changes, truncated);
            }
        }
    }

    private boolean compileNeo4j(String code) {
        try (var session = driver.session()) {
            var type = session.executeRead(transaction -> transaction.run("EXPLAIN " + code).consume().queryType(),
                    TransactionConfig.builder().withTimeout(Duration.ofSeconds(timeout)).build());
            if (type.name().equals("SCHEMA_WRITE")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "No se permiten cambios de esquema desde consultas guardadas");
            }
            return !type.name().equals("READ_ONLY");
        } catch (ClientException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Neo4j rechazó la sentencia Cypher. Revise la sintaxis y los tipos de datos");
        }
    }

    private Map<String, Object> response(List<Map<String, Object>> rows, Map<String, Object> changes, boolean truncated) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filas", rows);
        result.put("cambios", changes);
        result.put("truncado", truncated);
        return result;
    }

    private Object graphValue(Object value) {
        if (value instanceof Node node) return Map.of("id", node.elementId(), "propiedades", graphValue(node.asMap()));
        if (value instanceof Relationship relation) return Map.of("tipo", relation.type(), "propiedades", graphValue(relation.asMap()));
        if (value instanceof org.neo4j.driver.types.Path path) return path.toString();
        if (value instanceof TemporalAccessor || value instanceof java.time.Duration || value instanceof Point
                || value instanceof org.neo4j.driver.types.IsoDuration) return value.toString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(key.toString(), graphValue(item)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(this::graphValue).toList();
        return value;
    }
}
