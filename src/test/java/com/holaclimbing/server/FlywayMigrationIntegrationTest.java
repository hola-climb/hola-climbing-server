package com.holaclimbing.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway V1 migration creates core tables and pgvector extension")
    void flywayV1_createsCoreTablesAndVectorExtension() {
        Integer migrationTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'flyway_schema_history'
                """, Integer.class);
        Integer userTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'users'
                """, Integer.class);
        Integer vectorExtensionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class);

        assertThat(migrationTableCount).isEqualTo(1);
        assertThat(userTableCount).isEqualTo(1);
        assertThat(vectorExtensionCount).isEqualTo(1);
    }
}
