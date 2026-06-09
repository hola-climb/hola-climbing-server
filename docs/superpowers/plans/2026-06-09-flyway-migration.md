# Flyway Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move database bootstrapping from direct `db/schema.sql` execution to versioned Flyway migrations without breaking existing Testcontainers integration tests or manual migration history.

**Architecture:** Keep `db/schema.sql` as the human-readable schema snapshot for now, but make `src/main/resources/db/migration/V1__init.sql` the executable source for fresh database creation. Enable Flyway through Spring Boot auto-configuration, verify migrations on a pgvector-capable Testcontainers PostgreSQL image, and document how existing databases should baseline before production use.

**Tech Stack:** Java 25, Spring Boot 4, Flyway, PostgreSQL 16 + pgvector, MyBatis, JUnit 5, Testcontainers.

---

## Progress

- [x] Flyway starter and PostgreSQL Flyway database module added.
- [x] Testcontainers PostgreSQL image switched to `pgvector/pgvector:pg16`.
- [x] `V1__init.sql` created from the current `db/schema.sql` snapshot.
- [x] Flyway smoke test added and passing.
- [x] High-risk integration tests and full `./mvnw test` passing.
- [ ] Future schema changes should be added as `V{n}__*.sql` files and then reflected in `db/schema.sql`.

---

## Current Context

- `db/schema.sql` is currently applied manually with `psql`.
- `db/manual-migrations/` has idempotent drift fixes already used for non-fresh databases.
- Testcontainers PostgreSQL now uses `pgvector/pgvector:pg16`, which includes pgvector.
- `db/schema.sql` starts with `CREATE EXTENSION IF NOT EXISTS vector;`, so Flyway migration tests need a pgvector image.
- There is no `src/test/resources/application-test.yaml`; integration tests currently rely on `@Sql` scripts for domain-specific fixtures.

## File Map

- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/resources/db/migration/V1__init.sql`
- Modify: `src/test/java/com/holaclimbing/server/TestcontainersConfiguration.java`
- Create: `src/test/java/com/holaclimbing/server/FlywayMigrationIntegrationTest.java`
- Modify: `README.md`
- Optional Modify: `db/schema.sql`

---

### Task 1: Add Flyway Dependency And Pgvector Test Container

**Files:**
- Modify: `pom.xml`
- Modify: `src/test/java/com/holaclimbing/server/TestcontainersConfiguration.java`

- [x] **Step 1: Add Flyway dependencies**

Add under the database dependencies in `pom.xml`:

```xml
<!-- Flyway: fresh DB 생성과 운영 DB 변경 이력을 versioned migration으로 관리 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

- [x] **Step 2: Switch PostgreSQL Testcontainer image**

Change `TestcontainersConfiguration.postgresContainer()` to:

```java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres"));
}
```

- [x] **Step 3: Run dependency compile check**

Run:

```bash
./mvnw -DskipTests compile
```

Expected: compile succeeds and Flyway artifacts resolve.

---

### Task 2: Create V1 Init Migration

**Files:**
- Create: `src/main/resources/db/migration/V1__init.sql`
- Modify: `src/main/resources/application.yaml`

- [x] **Step 1: Create migration directory and V1**

Create `src/main/resources/db/migration/V1__init.sql` with the exact current content of `db/schema.sql`.

Use this check after creating it:

```bash
diff -u db/schema.sql src/main/resources/db/migration/V1__init.sql
```

Expected: no output.

- [x] **Step 2: Configure Flyway**

Add under `spring:` in `application.yaml`:

```yaml
  flyway:
    enabled: ${SPRING_FLYWAY_ENABLED:true}
    locations: classpath:db/migration
    baseline-on-migrate: ${SPRING_FLYWAY_BASELINE_ON_MIGRATE:false}
    validate-on-migrate: true
```

- [x] **Step 3: Run compile/resources check**

Run:

```bash
./mvnw -DskipTests test-compile
```

Expected: `V1__init.sql` is copied to `target/classes/db/migration`.

---

### Task 3: Write Migration Smoke Test

**Files:**
- Create: `src/test/java/com/holaclimbing/server/FlywayMigrationIntegrationTest.java`

- [x] **Step 1: Add migration integration test**

Create:

```java
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
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class);
        Integer userTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'users'",
                Integer.class);
        Integer vectorExtensionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class);

        assertThat(migrationCount).isGreaterThanOrEqualTo(1);
        assertThat(userTableCount).isEqualTo(1);
        assertThat(vectorExtensionCount).isEqualTo(1);
    }
}
```

- [x] **Step 2: Run RED/GREEN expectation**

Run:

```bash
./mvnw -Dtest=FlywayMigrationIntegrationTest test
```

Expected after Tasks 1-2: PASS. If it fails with `extension "vector" is not available`, the Testcontainers image was not switched to `pgvector/pgvector:pg16`.

---

### Task 4: Protect Existing Integration Tests

**Files:**
- Existing integration tests using `@Sql`.

- [x] **Step 1: Run the highest-risk test classes**

Run:

```bash
./mvnw -Dtest=UserAuthIntegrationTest,VideoIntegrationTest,RecommendationIntegrationTest,AnalysisIntegrationTest test
```

Expected: PASS.

- [x] **Step 2: If tests fail because Flyway-created tables conflict with `@Sql` scripts**

Apply the smallest fix:

```yaml
# src/test/resources/application-test.yaml
spring:
  flyway:
    enabled: true
```

No compatibility fix was needed; existing `@Sql` tests passed with Flyway enabled. Keep Flyway enabled first. Only set it to `false` if `@Sql` cleanup cannot be made compatible. If disabling is necessary, keep `FlywayMigrationIntegrationTest` enabled by adding:

```java
@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "spring.flyway.enabled=true"
})
```

- [x] **Step 3: Run all tests**

Run:

```bash
./mvnw test
```

Expected: PASS.

---

### Task 5: Document Fresh DB And Existing DB Paths

**Files:**
- Modify: `README.md`

- [x] **Step 1: Update setup command**

Replace manual schema apply instructions:

```bash
psql postgresql://hola:hola@localhost:5432/hola -f db/schema.sql
```

with:

```bash
./mvnw spring-boot:run
```

and explain that Flyway applies `src/main/resources/db/migration/V1__init.sql` on first startup.

- [x] **Step 2: Add existing DB baseline note**

Add:

```markdown
기존에 `db/schema.sql` 또는 `db/manual-migrations/*`로 이미 생성한 DB는 바로 Flyway V1을 실행하면
테이블 중복으로 실패할 수 있습니다. 이 경우 운영 반영 전에 백업을 만든 뒤 1회에 한해
`SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`로 기동해 현재 스키마를 baseline 처리하고,
이후에는 다시 `false`로 되돌립니다.
```

- [x] **Step 3: Run docs grep**

Run:

```bash
rg -n "schema.sql|Flyway|baseline" README.md
```

Expected: README distinguishes fresh DB migration from existing DB baseline.

---

### Task 6: Final Verification

**Files:**
- All modified files above.

- [x] **Step 1: Run Flyway targeted tests**

Run:

```bash
./mvnw -Dtest=FlywayMigrationIntegrationTest test
```

Expected: PASS.

- [x] **Step 2: Run full suite**

Run:

```bash
./mvnw test
```

Expected: PASS.

- [x] **Step 3: Check generated drift**

Run:

```bash
git diff --check
diff -u db/schema.sql src/main/resources/db/migration/V1__init.sql
```

Expected: whitespace check passes and V1 matches the schema snapshot.

## Trade-Offs

- **Pros:** Fresh DB setup becomes repeatable, CI/staging/prod schema creation becomes auditable, and future schema changes can stop accumulating as one-off manual scripts.
- **Cons:** Existing databases need a careful one-time baseline path; Testcontainers must use a pgvector image; `db/schema.sql` and `V1__init.sql` can drift unless a check is kept.
- **Rollback Cost:** Medium. Remove Flyway dependencies/config and migration file, then return to manual `psql -f db/schema.sql`. Existing DBs already baselined by Flyway would keep `flyway_schema_history`, which should not be dropped without a backup.
