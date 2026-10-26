package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.exception.ApiException;
import com.db2.proyectofinaldb2.repository.EvaluationRepository;
import com.db2.proyectofinaldb2.util.MongoJson;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final EvaluationRepository repository;

    public CatalogService(EvaluationRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> get(String type) {
        List<Document> pipeline = switch (type.toLowerCase(Locale.ROOT)) {
            case "materias" -> List.of(
                    groupWithSet("$materia"),
                    projectValue("$_id", "$_id", null),
                    sort("count", -1));
            case "carreras" -> List.of(
                    groupWithSet("$carrera_objetivo"),
                    projectValue("$_id", "$_id", null),
                    sort("count", -1));
            case "departamentos" -> List.of(
                    groupWithSet("$departamento_ie"),
                    projectValue("$_id", "$_id", null),
                    sort("count", -1));
            case "tipos" -> List.of(
                    groupWithSet("$tipo_ie"),
                    projectValue("$_id", "$_id", null),
                    sort("count", -1));
            case "municipios" -> List.of(
                    new Document("$group", new Document("_id", new Document("municipio", "$municipio_ie")
                            .append("departamento", "$departamento_ie"))
                            .append("aspirantes", new Document("$addToSet", "$correlativo_aspirante"))),
                    new Document("$project", new Document("_id", 0)
                            .append("value", "$_id.municipio")
                            .append("label", new Document("$concat", List.of(
                                    "$_id.municipio", " (", "$_id.departamento", ")")))
                            .append("departamento", "$_id.departamento")
                            .append("count", new Document("$size", "$aspirantes"))),
                    sort("count", -1));
            case "anios" -> List.of(
                    new Document("$group", new Document("_id", "$anio_ingreso")
                            .append("count", new Document("$sum", 1))),
                    new Document("$project", new Document("_id", 0)
                            .append("value", "$_id")
                            .append("label", new Document("$toString", "$_id"))
                            .append("count", 1)),
                    sort("value", 1));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Catálogo no válido: " + type);
        };
        return MongoJson.toList(
                repository.aggregate(EvaluationRepository.EVALUATIONS, pipeline));
    }

    private Document groupWithSet(String field) {
        return new Document("$group", new Document("_id", field)
                .append("aspirantes", new Document("$addToSet", "$correlativo_aspirante")));
    }

    private Document projectValue(String valueExpression, String labelExpression, String extraField) {
        Document project = new Document("_id", 0)
                .append("value", valueExpression)
                .append("label", labelExpression)
                .append("count", new Document("$size", "$aspirantes"));
        if (extraField != null) {
            project.append(extraField, "$_id." + extraField);
        }
        return new Document("$project", project);
    }

    private Document sort(String field, int direction) {
        return new Document("$sort", new Document(field, direction));
    }
}
