package com.db2.proyectofinaldb2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DotenvEnvironmentPostProcessorTest {

    @TempDir
    Path directory;

    @Test
    void loadsUppercaseKeysAndQuotedValuesWithoutPublishingSecrets() throws Exception {
        Files.writeString(directory.resolve(".env"), """
                SPRING_NEO4J_AUTHENTICATION_PASSWORD="private test value #1"
                APP_ETL_BATCH_SIZE=23
                APP_ETL_TEMP_DIR=
                """);
        StandardEnvironment environment = new StandardEnvironment();

        DotenvEnvironmentPostProcessor.load(directory, environment);

        assertEquals("private test value #1", environment.getProperty("spring.neo4j.authentication.password"));
        assertEquals(23, environment.getProperty("app.etl.batch-size", Integer.class));
        assertEquals("", environment.getProperty("app.etl.temp-dir"));
    }

    @Test
    void keepsHigherPriorityConfigurationAboveTheLocalFile() throws Exception {
        Files.writeString(directory.resolve(".env"), "APP_ETL_BATCH_SIZE=23\n");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("deploymentOverride",
                Map.of("app.etl.batch-size", "47")));

        DotenvEnvironmentPostProcessor.load(directory, environment);

        assertEquals(47, environment.getProperty("app.etl.batch-size", Integer.class));
    }

    @Test
    void allowsDeploymentsToProvideConfigurationWithoutALocalFile() {
        StandardEnvironment environment = new StandardEnvironment();

        DotenvEnvironmentPostProcessor.load(directory, environment);

        assertNull(environment.getProperty("audit.test.absent"));
    }
}
