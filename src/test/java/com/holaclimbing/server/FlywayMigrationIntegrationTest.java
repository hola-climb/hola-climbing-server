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

    @Test
    @DisplayName("Flyway migrations seed requested brand gyms and audited grade scales")
    void flywayMigrations_seedBrandGymsAndTheClimbPinkGrade() {
        Integer requestedGymCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM gyms
                WHERE deleted_at IS NULL
                  AND status = 'active'
                  AND (
                      name LIKE '더클라임%'
                      OR name LIKE '클라이밍파크%'
                      OR name LIKE '서울숲클라이밍%'
                      OR name LIKE '피커스%'
                      OR name LIKE '알레클라%'
                      OR name LIKE '%담장%'
                      OR name IN ('락트리클라이밍 분당', '스톤즈클라이밍', '피크닉클라이밍', '오프더월클라이밍')
                  )
                """, Integer.class);
        Integer requestedActiveGradeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM gym_grades grade
                JOIN gyms gym ON gym.id = grade.gym_id
                WHERE gym.deleted_at IS NULL
                  AND gym.status = 'active'
                  AND grade.is_active = TRUE
                  AND (
                      gym.name LIKE '더클라임%'
                      OR gym.name LIKE '클라이밍파크%'
                      OR gym.name LIKE '서울숲클라이밍%'
                      OR gym.name LIKE '피커스%'
                      OR gym.name LIKE '알레클라%'
                      OR gym.name LIKE '%담장%'
                      OR gym.name IN ('락트리클라이밍 분당', '스톤즈클라이밍', '피크닉클라이밍', '오프더월클라이밍')
                  )
                """, Integer.class);
        Integer theClimbPinkCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM gyms gym
                WHERE gym.deleted_at IS NULL
                  AND gym.name LIKE '더클라임%'
                  AND EXISTS (
                      SELECT 1
                      FROM gym_grades grade
                      WHERE grade.gym_id = gym.id
                        AND grade.label = '핑크'
                        AND grade.difficulty_order = 65
                        AND grade.is_active = TRUE
                  )
                """, Integer.class);

        assertThat(requestedGymCount).isEqualTo(32);
        assertThat(requestedActiveGradeCount).isEqualTo(320);
        assertThat(theClimbPinkCount).isEqualTo(12);
        assertActiveGrades(
                "http://place.map.kakao.com/1455205552",
                "흰색 > 노랑 > 연두 > 초록 > 파랑 > 빨강 > 회색 > 갈색 > 핑크 > 검정");
        assertActiveGrades(
                "http://place.map.kakao.com/1615143674",
                "빨강 > 주황 > 노랑 > 초록 > 하늘색 > 남색 > 보라 > 갈색 > 검정 > 핑크");
        assertActiveGrades(
                "http://place.map.kakao.com/662516308",
                "빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 회색 > 갈색 > 검정 > 흰색");
        assertActiveGrades(
                "http://place.map.kakao.com/444613083",
                "빨강 > 주황 > 노랑 > 초록 > 파랑 > 남색 > 보라 > 흰색 > 검정");
    }

    private void assertActiveGrades(String website, String expectedGrades) {
        String activeGrades = jdbcTemplate.queryForObject("""
                SELECT string_agg(grade.label, ' > ' ORDER BY grade.difficulty_order, grade.id)
                FROM gyms gym
                JOIN gym_grades grade ON grade.gym_id = gym.id
                WHERE gym.deleted_at IS NULL
                  AND gym.website = ?
                  AND grade.is_active = TRUE
                """, String.class, website);

        assertThat(activeGrades).isEqualTo(expectedGrades);
    }
}
