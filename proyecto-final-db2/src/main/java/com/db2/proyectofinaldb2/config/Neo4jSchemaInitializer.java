package com.db2.proyectofinaldb2.config;

import com.db2.proyectofinaldb2.neo4j.CypherCatalog;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates Neo4j constraints and indexes at startup. Failures are logged but do
 * not prevent the application from starting (Neo4j may not be running yet).
 */
@Component
public class Neo4jSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);
    private final Driver driver;

    public Neo4jSchemaInitializer(Driver driver) {
        this.driver = driver;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Session session = driver.session()) {
            for (String statement : CypherCatalog.CONSTRAINTS) {
                session.run(statement).consume();
            }
            for (String statement : CypherCatalog.INDEXES) {
                session.run(statement).consume();
            }
            log.info("Esquema de Neo4j verificado correctamente");
        } catch (Exception exception) {
            log.warn("No se pudo inicializar el esquema de Neo4j: {}", exception.getMessage());
        }
    }
}
