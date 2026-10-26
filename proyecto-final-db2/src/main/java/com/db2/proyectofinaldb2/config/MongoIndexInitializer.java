package com.db2.proyectofinaldb2.config;

import com.db2.proyectofinaldb2.repository.EvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the MongoDB indexes at startup. If MongoDB is not available yet the
 * application still starts; the ETL retries the index creation before loading.
 */
@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);
    private final EvaluationRepository repository;

    public MongoIndexInitializer(EvaluationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            repository.ensureIndexes();
        } catch (Exception exception) {
            log.warn("No se pudieron crear los índices de MongoDB: {}", exception.getMessage());
        }
    }
}
