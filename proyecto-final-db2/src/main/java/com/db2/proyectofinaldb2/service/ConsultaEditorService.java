package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.exception.ApiException;
import com.db2.proyectofinaldb2.util.MongoJson;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.bson.Document;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Point;
import org.neo4j.driver.types.Relationship;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Runs edited versions of the queries shown in the interface. Only read
 * operations are accepted and every response is capped and time limited.
 */
@Service
public class ConsultaEditorService {

    private static final Pattern PARAMETER_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_CODE_LENGTH = 20000;

    private final MongoTemplate mongo;
    private final Driver driver;
    private final CustomQueryPolicy policy;
    private final int maxRows;
    private final int timeout;

    public ConsultaEditorService(MongoTemplate mongo,
                                 Driver driver,
                                 CustomQueryPolicy policy,
                                 @Value("${app.queries.max-rows:200}") int maxRows,
                                 @Value("${app.queries.timeout-seconds:10}") int timeout) {
        this.mongo = mongo;
        this.driver = driver;
        this.policy = policy;
        this.maxRows = Math.max(1, Math.min(maxRows, 500));
        this.timeout = Math.max(1, Math.min(timeout, 60));
    }

    public Map<String, Object> executeMongo(String code) {
        long start = System.currentTimeMillis();
        Document command = policy.mongo(code);
        if (policy.writes("MONGODB", code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El editor solo permite lectura: comandos JSON de MongoDB con find o aggregation pipeline sin $out ni $merge");
        }
        String operation = command.getString("operacion");
        var collection = mongo.getCollection(command.getString("coleccion"))
                .withTimeout(timeout, TimeUnit.SECONDS);
        List<Document> rows = new ArrayList<>();
        try {
            if ("find".equals(operation)) {
                var find = collection.find(command.get("filtro", Document.class))
                        .maxTime(timeout, TimeUnit.SECONDS)
                        .limit(maxRows + 1);
                if (command.get("sort") instanceof Document sort && !sort.isEmpty()) {
                    find = find.sort(sort);
                }
                find.into(rows);
            } else {
                try (var cursor = collection.aggregate(command.getList("pipeline", Document.class))
                        .maxTime(timeout, TimeUnit.SECONDS).iterator()) {
                    while (cursor.hasNext() && rows.size() <= maxRows) {
                        rows.add(cursor.next());
                    }
                }
            }
        } catch (MongoException exception) {
            if (exception.getCode() == 50) {
                throw new ApiException(HttpStatus.REQUEST_TIMEOUT,
                        "La consulta superó el tiempo máximo permitido");
            }
            if (exception instanceof MongoCommandException) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "MongoDB rechazó la consulta. Revise operadores, tipos de datos y nombres de campo");
            }
            throw exception;
        }
        return response(MongoJson.toList(rows), start);
    }

    public Map<String, Object> executeNeo4j(String code, Map<String, Object> parameters) {
        long start = System.currentTimeMillis();
        validateCode(code);
        if (policy.writes("NEO4J", code)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El editor solo permite lectura: no se admiten escrituras en sentencias Cypher");
        }
        Map<String, Object> params = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        validateParameters(params);
        try (Session session = driver.session()) {
            return session.executeRead(transaction -> {
                var result = transaction.run(code, params);
                List<Map<String, Object>> rows = new ArrayList<>();
                while (result.hasNext() && rows.size() <= maxRows) {
                    Record record = result.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    record.asMap().forEach((key, value) -> row.put(key, normalize(value)));
                    rows.add(row);
                }
                return response(rows, start);
            }, TransactionConfig.builder().withTimeout(Duration.ofSeconds(timeout)).build());
        } catch (ClientException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Neo4j rechazó la consulta. Revise la sintaxis, los parámetros y los tipos de datos");
        }
    }

    private Map<String, Object> response(List<?> rows, long start) {
        boolean truncated = rows.size() > maxRows;
        List<?> visible = rows.subList(0, Math.min(rows.size(), maxRows));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filas", visible);
        result.put("truncado", truncated);
        result.put("limiteFilas", maxRows);
        result.put("duracionMs", System.currentTimeMillis() - start);
        return result;
    }

    private void validateCode(String code) {
        if (code == null || code.isBlank() || code.length() > MAX_CODE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La consulta debe contener entre 1 y " + MAX_CODE_LENGTH + " caracteres");
        }
    }

    private void validateParameters(Map<String, Object> parameters) {
        if (parameters.size() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La consulta admite como máximo 50 parámetros");
        }
        parameters.forEach((name, value) -> {
            if (name == null || !PARAMETER_NAME.matcher(name).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Nombre de parámetro inválido: " + name);
            }
            if (!isAllowedValue(value, 0)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "El parámetro " + name + " contiene un valor no permitido");
            }
        });
    }

    private boolean isAllowedValue(Object value, int depth) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return true;
        }
        if (depth >= 5) {
            return false;
        }
        if (value instanceof List<?> list) {
            return list.stream().allMatch(item -> isAllowedValue(item, depth + 1));
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().allMatch(entry ->
                    entry.getKey() instanceof String && isAllowedValue(entry.getValue(), depth + 1));
        }
        return false;
    }

    private Object normalize(Object value) {
        if (value instanceof Node node) {
            Map<String, Object> properties = new LinkedHashMap<>();
            node.asMap().forEach((key, property) -> properties.put(key, normalize(property)));
            return Map.of("id", node.elementId(),
                    "tipo", node.labels().iterator().hasNext() ? node.labels().iterator().next() : "Nodo",
                    "propiedades", properties);
        }
        if (value instanceof Relationship relationship) {
            Map<String, Object> properties = new LinkedHashMap<>();
            relationship.asMap().forEach((key, property) -> properties.put(key, normalize(property)));
            return Map.of("id", relationship.elementId(), "tipo", relationship.type(),
                    "propiedades", properties);
        }
        if (value instanceof Path path) {
            return path.toString();
        }
        if (value instanceof org.neo4j.driver.Value neo4jValue) {
            return normalize(neo4jValue.asObject());
        }
        if (value instanceof java.time.temporal.TemporalAccessor || value instanceof Duration
                || value instanceof Point || value instanceof org.neo4j.driver.types.IsoDuration) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), normalize(item)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(normalize(item)));
            return result;
        }
        return value;
    }
}
