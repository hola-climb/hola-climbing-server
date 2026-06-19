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

    @Test
    @DisplayName("Flyway migrations seed current terms including location and full content")
    void flywayMigrations_seedCurrentTermsWithContent() {
        Integer activeTermCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM terms_versions
                WHERE version = '1.0'
                  AND effective_at <= NOW()
                """, Integer.class);
        Boolean locationRequired = jdbcTemplate.queryForObject("""
                SELECT is_required
                FROM terms_versions
                WHERE type = 'location'
                  AND version = '1.0'
                """, Boolean.class);
        String serviceContent = jdbcTemplate.queryForObject("""
                SELECT content
                FROM terms_versions
                WHERE type = 'service'
                  AND version = '1.0'
                """, String.class);
        String locationContent = jdbcTemplate.queryForObject("""
                SELECT content
                FROM terms_versions
                WHERE type = 'location'
                  AND version = '1.0'
                """, String.class);

        assertThat(activeTermCount).isEqualTo(4);
        assertThat(locationRequired).isFalse();
        assertThat(serviceContent).contains("Hola Climbing", "서비스 이용");
        assertThat(locationContent).contains("위치정보", "암장 인증");
    }

    @Test
    @DisplayName("Flyway migrations use TIMESTAMPTZ for every app timestamp column")
    void flywayMigrations_useTimestamptzForTimestampColumns() {
        Integer timestampWithoutTimeZoneCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_schema = c.table_schema
                 AND t.table_name = c.table_name
                WHERE c.table_schema = 'public'
                  AND t.table_type = 'BASE TABLE'
                  AND c.table_name <> 'flyway_schema_history'
                  AND c.data_type = 'timestamp without time zone'
                """, Integer.class);

        assertThat(timestampWithoutTimeZoneCount).isZero();
    }
}
