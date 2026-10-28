package com.db2.proyectofinaldb2.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Loads private local configuration before Spring creates connection beans. */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path workingDirectory = Path.of("").toAbsolutePath();
        Path backendDirectory = workingDirectory.resolve("proyecto-final-db2");
        Path directory = Files.isRegularFile(backendDirectory.resolve(".env"))
                ? backendDirectory : workingDirectory;
        load(directory, environment);
    }

    static void load(Path directory, ConfigurableEnvironment environment) {
        Dotenv dotenv = Dotenv.configure()
                .directory(directory.toString())
                .ignoreIfMissing()
                .load();
        Map<String, Object> values = new LinkedHashMap<>();
        dotenv.entries().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        // OS/CLI settings retain precedence; uppercase .env keys use Spring's relaxed lookup.
        environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource("privateDotenv", values));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
