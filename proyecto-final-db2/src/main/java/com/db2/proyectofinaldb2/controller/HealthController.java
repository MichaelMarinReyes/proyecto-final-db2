package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.repository.EvaluationRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.neo4j.driver.Driver;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/salud")
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final Driver driver;
    private final EvaluationRepository repository;

    public HealthController(MongoTemplate mongoTemplate, Driver driver, EvaluationRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.driver = driver;
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> health() {
        boolean mongoAvailable;
        String mongoDetail = "Conectado";
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            mongoAvailable = true;
        } catch (Exception exception) {
            mongoAvailable = false;
            mongoDetail = String.valueOf(exception.getMessage());
        }

        boolean neo4jAvailable;
        String neo4jDetail = "Conectado";
        try {
            driver.verifyConnectivity();
            neo4jAvailable = true;
        } catch (Exception exception) {
            neo4jAvailable = false;
            neo4jDetail = String.valueOf(exception.getMessage());
        }

        Map<String, Object> collections = new LinkedHashMap<>();
        if (mongoAvailable) {
            collections.put("evaluaciones", repository.count(EvaluationRepository.EVALUATIONS));
            collections.put("aspirantes", repository.count(EvaluationRepository.ASPIRANTS));
            collections.put("resumen_carrera", repository.count(EvaluationRepository.CAREER_SUMMARY));
            collections.put("registros_rechazados", repository.count(EvaluationRepository.REJECTED));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("estado", mongoAvailable && neo4jAvailable ? "OK" : "DEGRADADO");
        result.put("timestamp", Instant.now().toString());
        result.put("mongo", Map.of("disponible", mongoAvailable, "detalle", mongoDetail));
        result.put("neo4j", Map.of("disponible", neo4jAvailable, "detalle", neo4jDetail));
        result.put("colecciones", collections);
        return result;
    }
}
