package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.dto.GrafoDto;
import com.db2.proyectofinaldb2.dto.QueryResult;
import com.db2.proyectofinaldb2.dto.QueryView;
import com.db2.proyectofinaldb2.exception.ApiException;
import com.db2.proyectofinaldb2.neo4j.CypherCatalog;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Point;
import org.neo4j.driver.types.Relationship;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GraphService {

    private final Driver driver;

    public GraphService(Driver driver) {
        this.driver = driver;
    }

    public List<Map<String, Object>> runQuery(int number,
                                              String carrera,
                                              String depto,
                                              String materia,
                                              String tipo,
                                              Integer anio,
                                              Integer limit) {
        return runQueryWithPlan(number, carrera, depto, materia, tipo, anio, limit).filas();
    }

    public QueryResult runQueryWithPlan(int number,
                                        String carrera,
                                        String depto,
                                        String materia,
                                        String tipo,
                                        Integer anio,
                                        Integer limit) {
        String query = switch (number) {
            case 1 -> CypherCatalog.G1_SUBGRAPH_BY_CAREER;
            case 2 -> CypherCatalog.G2_CAREERS_BY_DEPARTMENT;
            case 3 -> CypherCatalog.G3_APPROVED_BY_SUBJECT;
            case 4 -> CypherCatalog.G4_CO_ASPIRANTS;
            case 5 -> CypherCatalog.G5_SHARED_SUBJECTS;
            case 6 -> CypherCatalog.G6_FULL_PATH;
            case 7 -> CypherCatalog.G7_SHORTEST_PATH;
            default -> throw new ApiException(HttpStatus.NOT_FOUND,
                    "La consulta de grafo " + number + " no existe. Las consultas válidas van de 1 a 7");
        };

        Map<String, Object> params = new HashMap<>();
        params.put("carrera", blankToNull(carrera));
        params.put("depto", blankToNull(depto));
        params.put("materia", blankToNull(materia));
        params.put("tipo", blankToNull(tipo));
        params.put("anio", anio);
        params.put("limite", limit == null ? 100 : Math.min(Math.max(limit, 1), 500));

        if (number == 7 && (params.get("depto") == null || params.get("carrera") == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Para calcular la ruta más corta debe seleccionar un departamento y una carrera");
        }

        try (Session session = driver.session()) {
            List<Map<String, Object>> rows = session.executeRead(transaction -> transaction.run(query, params)
                    .list(record -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        record.asMap().forEach((key, value) -> row.put(key, normalize(value)));
                        return row;
                    }));
            return new QueryResult(rows, new QueryView("NEO4J", query, null, new LinkedHashMap<>(params)));
        }
    }

    public GrafoDto visualization(String carrera,
                                  String depto,
                                  String materia,
                                  String tipo,
                                  Integer anio,
                                  Integer limit) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder query = new StringBuilder(CypherCatalog.VISUALIZATION_BASE);

        if (notBlank(carrera)) {
            query.append(" AND c.nombre = $carrera");
            params.put("carrera", carrera);
        }
        if (notBlank(depto)) {
            query.append(" AND d.nombre = $depto");
            params.put("depto", depto);
        }
        if (notBlank(tipo)) {
            query.append(" AND i.tipo = $tipo");
            params.put("tipo", tipo);
        }
        if (notBlank(materia)) {
            query.append(" AND mat.nombre = $materia");
            params.put("materia", materia);
        }
        if (anio != null) {
            query.append(" AND e.anio_ingreso = $anio");
            params.put("anio", anio);
        }

        int safeLimit = limit == null ? 150 : Math.min(Math.max(limit, 1), 500);
        query.append(" RETURN a, i, mu, d, c, e, mat LIMIT $limite");
        params.put("limite", safeLimit);

        Map<String, GrafoDto.Nodo> nodes = new LinkedHashMap<>();
        Map<String, GrafoDto.Relacion> links = new LinkedHashMap<>();

        try (Session session = driver.session()) {
            List<Record> records = session.executeRead(transaction ->
                    transaction.run(query.toString(), params).list());
            for (Record record : records) {
                Node aspirant = node(record, "a");
                Node institution = node(record, "i");
                Node municipality = node(record, "mu");
                Node department = node(record, "d");
                Node career = node(record, "c");
                Node evaluation = node(record, "e");
                Node subject = node(record, "mat");

                addNode(nodes, aspirant);
                addNode(nodes, institution);
                addNode(nodes, municipality);
                addNode(nodes, department);
                addNode(nodes, career);
                addNode(nodes, evaluation);
                addNode(nodes, subject);

                addLink(links, aspirant, "PROCEDE_DE", institution);
                addLink(links, institution, "UBICADA_EN", municipality);
                addLink(links, municipality, "PERTENECE_A", department);
                addLink(links, aspirant, "ASPIRA_A", career);
                if (evaluation != null) {
                    addLink(links, aspirant, "REALIZO", evaluation);
                    addLink(links, evaluation, "DE_MATERIA", subject);
                    addLink(links, evaluation, "PARA_CARRERA", career);
                }
            }
        }

        return new GrafoDto(new ArrayList<>(nodes.values()), new ArrayList<>(links.values()));
    }

    public Map<String, Object> nodeDetail(String id) {
        try (Session session = driver.session()) {
            List<Record> records = session.executeRead(transaction ->
                    transaction.run(CypherCatalog.NODE_DETAIL, Map.of("id", id)).list());
            if (records.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "No se encontró el nodo solicitado");
            }
            Record record = records.getFirst();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("nodo", normalize(record.get("n")));
            result.put("relaciones", normalize(record.get("relaciones")));
            result.put("vecinos", normalize(record.get("vecinos")));
            return result;
        }
    }

    private void addNode(Map<String, GrafoDto.Nodo> nodes, Node node) {
        if (node == null) {
            return;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        node.asMap().forEach((key, value) -> properties.put(key, normalize(value)));
        String label = node.labels().iterator().hasNext()
                ? node.labels().iterator().next()
                : "Nodo";
        nodes.putIfAbsent(node.elementId(),
                new GrafoDto.Nodo(node.elementId(), label, label, properties));
    }

    private void addLink(Map<String, GrafoDto.Relacion> links, Node source, String type, Node target) {
        if (source == null || target == null) {
            return;
        }
        String id = source.elementId() + "-" + type + "-" + target.elementId();
        links.putIfAbsent(id, new GrafoDto.Relacion(
                id, source.elementId(), target.elementId(), type, Map.of()));
    }

    private Node node(Record record, String key) {
        Value value = record.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        Object object = value.asObject();
        return object instanceof Node node ? node : null;
    }

    private Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Value neo4jValue) {
            return normalize(neo4jValue.asObject());
        }
        if (value instanceof Node node) {
            return nodeToMap(node);
        }
        if (value instanceof Relationship relationship) {
            return relationshipToMap(relationship);
        }
        if (value instanceof TemporalAccessor || value instanceof java.time.Duration || value instanceof Point) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), normalize(mapValue)));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(normalize(item)));
            return result;
        }
        return value;
    }

    private Map<String, Object> nodeToMap(Node node) {
        Map<String, Object> properties = new LinkedHashMap<>();
        node.asMap().forEach((key, value) -> properties.put(key, normalize(value)));
        String label = node.labels().iterator().hasNext()
                ? node.labels().iterator().next()
                : "Nodo";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", node.elementId());
        result.put("tipo", label);
        result.put("label", label);
        result.put("propiedades", properties);
        return result;
    }

    private Map<String, Object> relationshipToMap(Relationship relationship) {
        Map<String, Object> properties = new LinkedHashMap<>();
        relationship.asMap().forEach((key, value) -> properties.put(key, normalize(value)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", relationship.elementId());
        result.put("tipo", relationship.type());
        result.put("source", relationship.startNodeElementId());
        result.put("target", relationship.endNodeElementId());
        result.put("propiedades", properties);
        return result;
    }

    private String blankToNull(String value) {
        return notBlank(value) ? value : null;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
