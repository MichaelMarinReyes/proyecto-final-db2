package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.exception.ApiException;
import com.db2.proyectofinaldb2.repository.EvaluationRepository;
import com.db2.proyectofinaldb2.util.MongoJson;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final MongoTemplate mongoTemplate;
    private final EvaluationRepository repository;

    public SearchService(MongoTemplate mongoTemplate, EvaluationRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    public List<Map<String, Object>> search(String type, String text) {
        if (type == null || type.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el tipo de búsqueda");
        }
        if (text == null || text.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe ingresar un término de búsqueda");
        }
        String query = text.trim();
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "aspirante" -> searchAspirants(query);
            case "carrera" -> searchCareers(query);
            case "institucion" -> searchInstitutions(query);
            case "materia" -> searchSubjects(query);
            case "municipio" -> searchMunicipalities(query);
            case "departamento" -> searchDepartments(query);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Tipo de búsqueda no válido: " + type);
        };
    }

    public Map<String, Object> getAspirant(String correlativo) {
        Document document = repository.findAspirante(correlativo);
        if (document == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "No se encontró el aspirante con correlativo " + correlativo);
        }
        return MongoJson.toMap(document);
    }

    public List<Map<String, Object>> getHistory(String correlativo) {
        return MongoJson.toList(repository.findEvaluationsByAspirante(correlativo));
    }

    private List<Map<String, Object>> searchAspirants(String text) {
        Query query = new Query(Criteria.where("_id")
                .regex(java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(text),
                        java.util.regex.Pattern.CASE_INSENSITIVE)))
                .limit(50);
        query.fields().include("_id", "sexo", "total_evaluaciones", "materias", "carreras_objetivo");
        return MongoJson.toList(mongoTemplate.find(query, Document.class, EvaluationRepository.ASPIRANTS));
    }

    private List<Map<String, Object>> searchCareers(String text) {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("carrera_objetivo", regex(text))),
                new Document("$group", new Document("_id", "$carrera_objetivo")
                        .append("aspirantes", new Document("$addToSet", "$correlativo_aspirante"))),
                new Document("$project", new Document("_id", 0)
                        .append("carrera", "$_id")
                        .append("total_aspirantes", new Document("$size", "$aspirantes"))),
                new Document("$sort", new Document("total_aspirantes", -1)),
                new Document("$limit", 50)
        );
        return run(pipeline);
    }

    private List<Map<String, Object>> searchSubjects(String text) {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("materia", regex(text))),
                new Document("$group", new Document("_id", "$materia")
                        .append("evaluaciones", new Document("$sum", 1))),
                new Document("$project", new Document("_id", 0)
                        .append("materia", "$_id")
                        .append("evaluaciones", 1)),
                new Document("$sort", new Document("evaluaciones", -1)),
                new Document("$limit", 50)
        );
        return run(pipeline);
    }

    private List<Map<String, Object>> searchInstitutions(String text) {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("$or", List.of(
                        new Document("tipo_ie", regex(text)),
                        new Document("municipio_ie", regex(text)),
                        new Document("departamento_ie", regex(text))))),
                new Document("$group", new Document("_id", new Document("tipo", "$tipo_ie")
                        .append("municipio", "$municipio_ie")
                        .append("departamento", "$departamento_ie"))
                        .append("aspirantes", new Document("$addToSet", "$correlativo_aspirante"))),
                new Document("$project", new Document("_id", 0)
                        .append("institucion", new Document("$concat", List.of(
                                "$_id.tipo", " - ", "$_id.municipio", " - ", "$_id.departamento")))
                        .append("tipo", "$_id.tipo")
                        .append("municipio", "$_id.municipio")
                        .append("departamento", "$_id.departamento")
                        .append("total_aspirantes", new Document("$size", "$aspirantes"))),
                new Document("$sort", new Document("total_aspirantes", -1)),
                new Document("$limit", 50)
        );
        return run(pipeline);
    }

    private List<Map<String, Object>> searchMunicipalities(String text) {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("$or", List.of(
                        new Document("municipio_ie", regex(text)),
                        new Document("departamento_ie", regex(text))))),
                new Document("$group", new Document("_id", new Document("municipio", "$municipio_ie")
                        .append("departamento", "$departamento_ie"))
                        .append("aspirantes", new Document("$addToSet", "$correlativo_aspirante"))),
                new Document("$project", new Document("_id", 0)
                        .append("municipio", "$_id.municipio")
                        .append("departamento", "$_id.departamento")
                        .append("total_aspirantes", new Document("$size", "$aspirantes"))),
                new Document("$sort", new Document("total_aspirantes", -1)),
                new Document("$limit", 50)
        );
        return run(pipeline);
    }

    private List<Map<String, Object>> searchDepartments(String text) {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("departamento_ie", regex(text))),
                new Document("$group", new Document("_id", "$departamento_ie")
                        .append("aspirantes", new Document("$addToSet", "$correlativo_aspirante"))),
                new Document("$project", new Document("_id", 0)
                        .append("departamento", "$_id")
                        .append("total_aspirantes", new Document("$size", "$aspirantes"))),
                new Document("$sort", new Document("total_aspirantes", -1)),
                new Document("$limit", 50)
        );
        return run(pipeline);
    }

    private List<Map<String, Object>> run(List<Document> pipeline) {
        return MongoJson.toList(
                repository.aggregate(EvaluationRepository.EVALUATIONS, pipeline));
    }

    private Document regex(String text) {
        String escaped = text.replaceAll("([\\\\.^$|?*+()\\[\\]{}])", "\\\\$1");
        return new Document("$regex", escaped).append("$options", "i");
    }
}
