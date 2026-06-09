# Pgvector Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the recommendation feed's "following first + latest" fallback with a pgvector-aware ranking when user and gym embeddings are available, while keeping the existing fallback for sparse data.

**Architecture:** Keep the public endpoint `GET /api/recommendations/videos` unchanged for V1. Add vector-aware SQL in the recommendation mapper that ranks videos by similarity between `users.style_embedding` and `gyms.style_embedding`, then falls back to following/latest when either embedding is missing. Do not add a new recommendation engine service yet; the ranking stays in MyBatis because PostgreSQL owns the vector operator and index.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis XML, PostgreSQL 16 + pgvector, JUnit 5, MockMvc, Testcontainers.

---

## Prerequisites And Task Split

Already completed by the Flyway task:
- [x] Testcontainers PostgreSQL image uses `pgvector/pgvector:pg16`.
- [x] Fresh DB migration creates the `vector` extension and base `style_embedding` columns.

Next pgvector work should be split into these commits/tasks:
- [ ] **Fixture alignment:** add pgvector extension/embedding columns back to focused test SQL fixtures.
- [ ] **Red behavior test:** prove current recommendation order ignores vector similarity.
- [ ] **Mapper ranking:** add vector-distance ordering with following/latest fallback.
- [ ] **Docs and verification:** update README semantics and run recommendation-related tests.

---

## Current Context

- `db/schema.sql` already enables `CREATE EXTENSION IF NOT EXISTS vector`.
- `src/main/resources/db/migration/V1__init.sql` also enables `vector` on fresh DBs.
- `users.style_embedding vector(64)` and `gyms.style_embedding vector(64)` already exist.
- IVFFlat indexes already exist on `users.style_embedding` and `gyms.style_embedding`.
- The domain models intentionally ignore embedding fields today.
- `com.pgvector:pgvector:0.1.6` is already in `pom.xml`, but no MyBatis type handler exists.
- Current recommendation endpoint returns `PageResponse<RecommendedVideoResponse>` with `source` set to `following` or `recommended`.
- Focused test fixture SQL currently excludes `style_embedding` because it was written for plain PostgreSQL; this can now be restored because Testcontainers uses pgvector.

## File Map

- Modify: `src/main/resources/mapper/recommendation/RecommendationMapper.xml`
- Modify: `src/main/java/com/holaclimbing/server/domain/recommendation/mapper/RecommendationMapper.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/recommendation/dto/response/RecommendedVideoResponse.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/recommendation/service/RecommendationServiceImpl.java`
- Modify: `src/test/java/com/holaclimbing/server/domain/recommendation/RecommendationIntegrationTest.java`
- Modify: `src/test/resources/sql/users-schema.sql`
- Modify: `src/test/resources/sql/gyms-schema.sql`
- Modify: `src/test/resources/sql/gyms-data.sql`
- Optional Modify: `README.md`

---

### Task 1: Add Pgvector Test Fixtures

**Files:**
- Modify: `src/test/resources/sql/users-schema.sql`
- Modify: `src/test/resources/sql/gyms-schema.sql`
- Modify: `src/test/resources/sql/gyms-data.sql`

- [ ] **Step 1: Add vector extension and embedding columns to test schemas**

Add near the top of `users-schema.sql` and `gyms-schema.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Add to `users` in `users-schema.sql`:

```sql
    style_embedding vector(64),
```

Add to `gyms` in `gyms-schema.sql`:

```sql
    style_embedding vector(64),
```

- [ ] **Step 2: Add deterministic gym embeddings**

In `gyms-data.sql`, update the two seeded gyms after insert:

```sql
UPDATE gyms
SET style_embedding = ('[' || array_to_string(ARRAY(
    SELECT CASE WHEN i = 1 THEN 1.0 ELSE 0.0 END
    FROM generate_series(1, 64) AS i
), ',') || ']')::vector
WHERE id = 1;

UPDATE gyms
SET style_embedding = ('[' || array_to_string(ARRAY(
    SELECT CASE WHEN i = 2 THEN 1.0 ELSE 0.0 END
    FROM generate_series(1, 64) AS i
), ',') || ']')::vector
WHERE id = 2;
```

- [ ] **Step 3: Run existing recommendation tests**

Run:

```bash
./mvnw -Dtest=RecommendationIntegrationTest test
```

Expected: current tests still pass. The PostgreSQL test container image has already been switched to `pgvector/pgvector:pg16` in the Flyway task.

---

### Task 2: Write Red Test For Vector Ranking

**Files:**
- Modify: `src/test/java/com/holaclimbing/server/domain/recommendation/RecommendationIntegrationTest.java`

- [ ] **Step 1: Add helper to set user embedding**

Add helper:

```java
private void setUserEmbedding(String email, int hotDimension) {
    Long userId = userMapper.findByEmail(email).getId();
    String vector = "[" + java.util.stream.IntStream.rangeClosed(1, 64)
            .mapToObj(i -> i == hotDimension ? "1.0" : "0.0")
            .collect(java.util.stream.Collectors.joining(",")) + "]";
    jdbcTemplate.update("UPDATE users SET style_embedding = ?::vector WHERE id = ?", vector, userId);
}
```

Add field:

```java
@Autowired
private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
```

- [ ] **Step 2: Add failing behavior test**

Add test:

```java
@Test
@DisplayName("홈 피드 — 사용자 스타일 임베딩과 가까운 암장 영상이 먼저 노출된다")
void getVideoFeed_whenEmbeddingsExist_ordersByVectorSimilarity() throws Exception {
    String viewer = register("viewer@hola.com", "viewer");
    String nearUploader = register("near@hola.com", "nearuser");
    String farUploader = register("far@hola.com", "faruser");
    setUserEmbedding("viewer@hola.com", 2);

    createVideoAtGym(nearUploader, 2L, 2002L);
    createVideoAtGym(farUploader, 1L, 1002L);

    mockMvc.perform(get("/api/recommendations/videos").header("Authorization", "Bearer " + viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.content[0].gymName").value("TheClimb Hongdae"))
            .andExpect(jsonPath("$.data.content[0].source").value("recommended"));
}
```

Add a variant of `createVideo` that accepts gym and grade:

```java
private void createVideoAtGym(String token, Long gymId, Long gymGradeId) throws Exception {
    long userId = dataOf(mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())).path("userId").asLong();
    String path = "videos/uploads/" + userId + "/test-" + java.util.UUID.randomUUID() + ".mp4";
    var request = new CreateVideoRequest(gymId, "feed clip", "desc", gymGradeId,
            path, null, 30, java.time.LocalDate.of(2026, 6, 3), true);
    mockMvc.perform(post("/api/videos")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
}
```

- [ ] **Step 3: Run RED**

Run:

```bash
./mvnw -Dtest=RecommendationIntegrationTest#getVideoFeed_whenEmbeddingsExist_ordersByVectorSimilarity test
```

Expected: test fails because current SQL orders by following/latest rather than vector similarity.

---

### Task 3: Implement Vector-Aware Ranking With Fallback

**Files:**
- Modify: `src/main/resources/mapper/recommendation/RecommendationMapper.xml`

- [ ] **Step 1: Update ranking SQL**

Replace the `ORDER BY` in `findFeedVideos` with a ranked CTE:

```sql
WITH viewer AS (
    SELECT style_embedding
    FROM users
    WHERE id = #{userId}
),
feed AS (
    SELECT v.id, v.user_id, v.gym_id, v.gym_grade_id,
           g.name AS gym_name,
           gg.label AS gym_grade_label,
           gg.difficulty_order AS gym_grade_difficulty_order,
           v.title, v.description,
           v.gcs_path, v.gcs_streaming_path, v.thumbnail_path,
           v.duration_seconds, v.recorded_date, v.file_size_bytes, v.mime_type,
           v.view_count, v.like_count, v.comment_count, v.status, v.is_public,
           v.created_at, v.updated_at, v.deleted_at,
           f.id IS NOT NULL AS is_following,
           CASE
               WHEN viewer.style_embedding IS NOT NULL AND g.style_embedding IS NOT NULL
               THEN viewer.style_embedding <=> g.style_embedding
               ELSE NULL
           END AS vector_distance
    FROM videos v
    JOIN gyms g ON g.id = v.gym_id
    JOIN gym_grades gg ON gg.id = v.gym_grade_id AND gg.gym_id = v.gym_id
    LEFT JOIN follows f ON f.following_id = v.user_id AND f.follower_id = #{userId}
    CROSS JOIN viewer
    WHERE v.is_public = TRUE AND v.deleted_at IS NULL AND v.status <> 'failed'
      AND v.user_id <> #{userId}
      AND NOT EXISTS (
          SELECT 1
          FROM user_blocks ub
          WHERE ub.blocker_id = #{userId}
            AND ub.blocked_id = v.user_id
      )
)
SELECT id, user_id, gym_id, gym_grade_id,
       gym_name, gym_grade_label, gym_grade_difficulty_order,
       title, description,
       gcs_path, gcs_streaming_path, thumbnail_path,
       duration_seconds, recorded_date, file_size_bytes, mime_type,
       view_count, like_count, comment_count, status, is_public,
       created_at, updated_at, deleted_at
FROM feed
ORDER BY
    (vector_distance IS NULL) ASC,
    vector_distance ASC,
    is_following DESC,
    created_at DESC,
    id DESC
LIMIT #{size} OFFSET #{offset}
```

- [ ] **Step 2: Run vector ranking test**

Run:

```bash
./mvnw -Dtest=RecommendationIntegrationTest#getVideoFeed_whenEmbeddingsExist_ordersByVectorSimilarity test
```

Expected: PASS.

- [ ] **Step 3: Run all recommendation tests**

Run:

```bash
./mvnw -Dtest=RecommendationIntegrationTest test
```

Expected: existing following-first behavior may need adjustment. If following-first conflicts with vector ranking, make the test explicit: following-first applies only when viewer embedding is null.

---

### Task 4: Document Recommendation Semantics

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace "고도화 예정" wording**

Change the pgvector bullet to say:

```markdown
- **pgvector 기반 추천** — 사용자의 `style_embedding`과 암장 `style_embedding`이 모두 있으면
  코사인 거리(`<=>`)로 가까운 암장 영상을 우선 노출합니다. 임베딩이 없는 초기 데이터는
  팔로잉 우선 + 최신순 휴리스틱으로 fallback합니다.
```

- [ ] **Step 2: Run documentation grep**

Run:

```bash
rg -n "pgvector 기반 추천|고도화 예정|휴리스틱" README.md
```

Expected: README clearly says vector ranking is active with fallback, not future-only.

---

### Task 5: Final Verification

**Files:**
- All modified files above.

- [ ] **Step 1: Run targeted tests**

Run:

```bash
./mvnw -Dtest=RecommendationIntegrationTest test
```

Expected: PASS.

- [ ] **Step 2: Run related integration tests**

Run:

```bash
./mvnw -Dtest=RecommendationIntegrationTest,VideoIntegrationTest,GymIntegrationTest test
```

Expected: PASS.

- [ ] **Step 3: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: exit 0.

## Trade-Offs

- **Pros:** Uses existing schema and pgvector indexes, keeps API stable, gives honest fallback for sparse embeddings.
- **Cons:** Recommendation still lives in SQL, not a separate ranking service. Cursor pagination remains deferred because vector distance + fallback sort is not a simple single-key keyset.
- **Rollback Cost:** Low. Revert only the recommendation mapper/test/README changes; schema is already present.
