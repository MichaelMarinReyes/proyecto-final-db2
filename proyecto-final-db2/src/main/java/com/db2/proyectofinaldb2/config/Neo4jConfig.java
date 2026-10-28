package com.db2.proyectofinaldb2.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fallback Neo4j driver definition in case the auto-configuration does not
 * provide one (it depends on the selected starter).
 */
@Configuration
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Driver.class)
    public Driver neo4jDriver(@Value("${spring.neo4j.uri}") String uri,
                              @Value("${spring.neo4j.authentication.username}") String username,
                              @Value("${spring.neo4j.authentication.password}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
