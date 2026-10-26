package com.db2.proyectofinaldb2.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncConfig {

    /**
     * Dedicated executor for ETL jobs so the HTTP request returns immediately
     * while the file keeps being processed in the background.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService etlExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "etl-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
