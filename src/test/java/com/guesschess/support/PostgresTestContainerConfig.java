package com.guesschess.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base Postgres partagee pour les tests d'integration Testcontainers de l'etape 4
 * (persistance JPA, securite). @ServiceConnection cable automatiquement le datasource
 * Spring Boot sur ce conteneur - pas besoin de proprietes manuelles.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
