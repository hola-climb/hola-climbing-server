# Video Gym Recommendations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build public `GET /api/videos/{videoId}/recommendations/gyms?lat=&lng=&radius=&size=` that recommends nearby gyms whose public analyzed videos use techniques and dynamic/static style similar to the reference video.

**Architecture:** Treat the API as a video subresource end to end. Put the controller, service, mapper, domain projection, and response DTO under `domain/video`, and keep existing `domain/recommendation` APIs untouched. The endpoint only uses public videos as recommendation seed/candidates; when the seed analysis or gym analysis data is sparse, it falls back to public nearby gym ordering by distance and rating.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis XML mapper, PostgreSQL JSONB, MockMvc integration tests with Testcontainers.

---

## File Structure

- Create: `src/main/java/com/holaclimbing/server/domain/video/VideoRecommendationController.java`
  - Public REST controller for `GET /api/videos/{videoId}/recommendations/gyms`.
  - Keeps recommendation endpoint discoverable under the `video` package without making `VideoController` larger.
- Create: `src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationService.java`
  - Service interface for video-seeded gym recommendation.
- Create: `src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationServiceImpl.java`
  - Validates coordinates and reference video accessibility.
  - Chooses similarity query or nearby fallback.
  - Resolves gym image URLs, business hours, `isOpen`, and optional authenticated favorite state.
- Create: `src/main/java/com/holaclimbing/server/domain/video/mapper/VideoGymRecommendationMapper.java`
  - MyBatis mapper for seed analysis lookup, similarity query, and nearby fallback query.
- Create: `src/main/java/com/holaclimbing/server/domain/video/domain/VideoRecommendedGym.java`
  - Query projection containing gym fields and score components.
- Create: `src/main/java/com/holaclimbing/server/domain/video/dto/response/VideoRecommendedGymResponse.java`
  - API response DTO with `similarityScore`, component scores, `source`, business hours, and open/favorite status.
- Create: `src/main/resources/mapper/video/VideoGymRecommendationMapper.xml`
  - SQL for video-based similarity and public nearby fallback.
- Modify: `src/test/java/com/holaclimbing/server/domain/video/VideoIntegrationTest.java`
  - Add integration tests for public access, ranking, fallback, private seed rejection, invalid coordinates, and optional favorite.
- Modify: `src/test/resources/sql/videos-schema.sql`
  - Add `analysis_video_results` table to the video test fixture because the new API belongs to the video domain test slice.
- Optional Modify: `README.md`
  - Add one line under the video/recommendation feature summary if API docs need to mention the new endpoint.

## API Contract

Endpoint:

```http
GET /api/videos/{videoId}/recommendations/gyms?lat=37.5000&lng=127.0200&radius=12&size=20
```

Authentication:

- Public. No token is required.
- If a valid token is present, use `viewerId` only for `isFavorite`; recommendation ranking must not depend on viewer identity.

Success response:

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "TheClimb Gangnam",
      "address": "Seoul Gangnam-gu",
      "thumbnailUrl": "https://...",
      "regionCode": "seoul",
      "ratingAvg": 4.50,
      "ratingCount": 120,
      "businessHours": {
        "mon": { "open": "06:00", "close": "23:00" }
      },
      "isOpen": true,
      "isFavorite": false,
      "distanceKm": 1.87,
      "similarityScore": 0.82,
      "techniqueScore": 0.70,
      "dynamicScore": 0.15,
      "locationRatingScore": 0.07,
      "analyzedVideoCount": 3,
      "source": "video_style_match"
    }
  ]
}
```

Fallback response uses the same shape, but `similarityScore`, `techniqueScore`, `dynamicScore`, and `analyzedVideoCount` are omitted because `@JsonInclude(NON_NULL)` is applied. `locationRatingScore` may be present for nearby rows, and `source` is `"nearby"`.

## Ranking Rules

Use these constants in `VideoGymRecommendationServiceImpl`:

```java
private static final int MIN_ANALYZED_VIDEOS_PER_GYM = 2;
private static final double TECHNIQUE_WEIGHT = 0.70;
private static final double DYNAMIC_WEIGHT = 0.15;
private static final double LOCATION_RATING_WEIGHT = 0.15;
```

Score definition:

```text
techniqueScore = matched reference techniques / reference technique count
dynamicScore = 1.0 when reference final_is_dynamic is null
dynamicScore = matching dynamic/static candidate video ratio when reference final_is_dynamic is true/false
locationRatingScore = distanceScore * 0.10 + ratingScore * 0.05
similarityScore = techniqueScore * 0.70 + dynamicScore * 0.15 + locationRatingScore
```

Distance and rating normalization:

```text
distanceScore = 1 - LEAST(distance_km / radiusKm, 1)
ratingScore = LEAST(rating_avg / 5, 1)
locationRatingScore = distanceScore * (0.10 / 0.15) + ratingScore * (0.05 / 0.15)
```

The response exposes `locationRatingScore` as the final weighted contribution in `similarityScore`, not the normalized 0..1 intermediate. This makes the score components add up:

```text
similarityScore = techniqueScoreContribution + dynamicScoreContribution + locationRatingScore
```

where `techniqueScore` and `dynamicScore` response fields are also weighted contributions:

```text
techniqueScore = rawTechniqueScore * 0.70
dynamicScore = rawDynamicScore * 0.15
```

Fallback rules:

- Seed video not found: throw `BusinessException(ErrorCode.VIDEO_NOT_FOUND)`.
- Seed video is private or deleted: throw `BusinessException(ErrorCode.VIDEO_NOT_ACCESSIBLE)`.
- Seed analysis row missing: return nearby fallback.
- Seed `final_techniques` is empty: return nearby fallback.
- No gym inside radius has at least `MIN_ANALYZED_VIDEOS_PER_GYM` public analyzed videos: return nearby fallback.

---

### Task 1: Add Video Test Fixture Support For Analysis Results

**Files:**
- Modify: `src/test/resources/sql/videos-schema.sql`

- [ ] **Step 1: Add the analysis table to the video test schema**

In `src/test/resources/sql/videos-schema.sql`, add the new drop before `DROP TABLE IF EXISTS videos CASCADE;`:

```sql
DROP TABLE IF EXISTS analysis_video_results CASCADE;
```

Then add this table after `CREATE TABLE videos (...)` and before `CREATE TABLE comments (...)`:

```sql
CREATE TABLE analysis_video_results (
    video_id                BIGINT PRIMARY KEY REFERENCES videos(id) ON DELETE CASCADE,
    model_version           VARCHAR(50),
    ai_techniques           JSONB NOT NULL DEFAULT '[]'::jsonb,
    ai_is_dynamic           BOOLEAN,
    ai_dynamic_probability  REAL,
    final_techniques        JSONB NOT NULL DEFAULT '[]'::jsonb,
    final_is_dynamic        BOOLEAN,
    feedback_applied        BOOLEAN NOT NULL DEFAULT FALSE,
    feedback_note           TEXT,
    corrected_by            BIGINT REFERENCES users(id) ON DELETE SET NULL,
    corrected_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_analysis_video_dynamic_probability
        CHECK (ai_dynamic_probability IS NULL OR (ai_dynamic_probability >= 0 AND ai_dynamic_probability <= 1))
);
```

- [ ] **Step 2: Run the existing video integration test to verify fixture compatibility**

Run:

```bash
./mvnw -Dtest=VideoIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Commit the fixture-only change**

```bash
git add src/test/resources/sql/videos-schema.sql
git commit -m "test(video): add analysis result fixture table"
```

---

### Task 2: Write Failing Integration Tests For Video-Based Gym Recommendations

**Files:**
- Modify: `src/test/java/com/holaclimbing/server/domain/video/VideoIntegrationTest.java`

- [ ] **Step 1: Add test imports**

Add these imports near the existing imports:

```java
import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
```

- [ ] **Step 2: Add ranking success test**

Add this test before the `// helpers` section:

```java
@Test
@DisplayName("영상 기반 암장 추천 — 기준 영상과 기술·dynamic 스타일이 비슷한 암장을 먼저 반환한다")
void getVideoGymRecommendations_ordersByTechniqueAndDynamicSimilarity() throws Exception {
    String seedOwner = register("seed-rec@hola.com", "seedrec");
    String uploader = register("gym-rec-uploader@hola.com", "gymrecuploader");

    long seedVideoId = createVideoAtGym(seedOwner, 1L, 1003L, true, "seed");
    insertVideoAnalysis(seedVideoId, List.of("high_step", "flagging"), true);

    long gymOneA = createVideoAtGym(uploader, 1L, 1003L, true, "gym-one-a");
    long gymOneB = createVideoAtGym(uploader, 1L, 1002L, true, "gym-one-b");
    insertVideoAnalysis(gymOneA, List.of("high_step", "flagging"), true);
    insertVideoAnalysis(gymOneB, List.of("high_step"), true);

    long gymTwoA = createVideoAtGym(uploader, 2L, 1004L, true, "gym-two-a");
    long gymTwoB = createVideoAtGym(uploader, 2L, 1005L, true, "gym-two-b");
    insertVideoAnalysis(gymTwoA, List.of("toe_hook"), false);
    insertVideoAnalysis(gymTwoB, List.of("heel_hook"), false);

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seedVideoId)
                    .param("lat", "37.5000")
                    .param("lng", "127.0200")
                    .param("radius", "12")
                    .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].source").value("video_style_match"))
            .andExpect(jsonPath("$.data[0].similarityScore", greaterThan(0.70)))
            .andExpect(jsonPath("$.data[0].techniqueScore", greaterThan(0.40)))
            .andExpect(jsonPath("$.data[0].dynamicScore", greaterThan(0.10)))
            .andExpect(jsonPath("$.data[0].locationRatingScore", greaterThan(0.0)))
            .andExpect(jsonPath("$.data[0].analyzedVideoCount").value(2))
            .andExpect(jsonPath("$.data[0].isFavorite").value(false))
            .andExpect(jsonPath("$.data[0].distanceKm").isNumber())
            .andExpect(jsonPath("$.data[0].businessHours").exists())
            .andExpect(jsonPath("$.data[1].id").value(2))
            .andExpect(jsonPath("$.data[1].source").value("video_style_match"))
            .andExpect(jsonPath("$.data[1].similarityScore", lessThanOrEqualTo(0.55)));
}
```

- [ ] **Step 3: Add fallback test for missing seed analysis**

Add this test:

```java
@Test
@DisplayName("영상 기반 암장 추천 — 기준 영상 분석 결과가 없으면 공개 nearby fallback을 반환한다")
void getVideoGymRecommendations_withoutSeedAnalysisFallsBackToNearby() throws Exception {
    String seedOwner = register("seed-nearby@hola.com", "seednearby");
    long seedVideoId = createVideoAtGym(seedOwner, 1L, 1003L, true, "seed-no-analysis");

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seedVideoId)
                    .param("lat", "37.5000")
                    .param("lng", "127.0200")
                    .param("radius", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].source").value("nearby"))
            .andExpect(jsonPath("$.data[0].similarityScore").doesNotExist())
            .andExpect(jsonPath("$.data[0].techniqueScore").doesNotExist())
            .andExpect(jsonPath("$.data[0].dynamicScore").doesNotExist())
            .andExpect(jsonPath("$.data[0].analyzedVideoCount").doesNotExist());
}
```

- [ ] **Step 4: Add fallback test for sparse gym analysis data**

Add this test:

```java
@Test
@DisplayName("영상 기반 암장 추천 — 반경 내 분석 영상이 부족하면 공개 nearby fallback을 반환한다")
void getVideoGymRecommendations_sparseGymAnalysisFallsBackToNearby() throws Exception {
    String seedOwner = register("seed-sparse@hola.com", "seedsparse");
    String uploader = register("sparse-uploader@hola.com", "sparseuploader");

    long seedVideoId = createVideoAtGym(seedOwner, 1L, 1003L, true, "seed");
    insertVideoAnalysis(seedVideoId, List.of("high_step", "flagging"), true);

    long onlyOneAnalyzed = createVideoAtGym(uploader, 1L, 1002L, true, "only-one");
    insertVideoAnalysis(onlyOneAnalyzed, List.of("high_step"), true);

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seedVideoId)
                    .param("lat", "37.5000")
                    .param("lng", "127.0200")
                    .param("radius", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].source").value("nearby"))
            .andExpect(jsonPath("$.data[0].similarityScore").doesNotExist());
}
```

- [ ] **Step 5: Add private seed access test**

Add this test:

```java
@Test
@DisplayName("영상 기반 암장 추천 실패 — 비공개 기준 영상은 공개 API에서도 403")
void getVideoGymRecommendations_privateSeedReturns403() throws Exception {
    String seedOwner = register("seed-private@hola.com", "seedprivate");
    long privateVideoId = createVideoAtGym(seedOwner, 1L, 1003L, false, "private-seed");
    insertVideoAnalysis(privateVideoId, List.of("high_step"), true);

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", privateVideoId)
                    .param("lat", "37.5000")
                    .param("lng", "127.0200"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("V006"));
}
```

- [ ] **Step 6: Add missing seed test**

Add this test:

```java
@Test
@DisplayName("영상 기반 암장 추천 실패 — 없는 기준 영상은 404 V001")
void getVideoGymRecommendations_missingSeedReturns404() throws Exception {
    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", 999_999L)
                    .param("lat", "37.5000")
                    .param("lng", "127.0200"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("V001"));
}
```

- [ ] **Step 7: Add invalid coordinate test**

Add this test:

```java
@Test
@DisplayName("영상 기반 암장 추천 실패 — 좌표나 반경이 유효하지 않으면 400")
void getVideoGymRecommendations_invalidCoordinatesReturn400() throws Exception {
    String seedOwner = register("seed-invalid@hola.com", "seedinvalid");
    long seedVideoId = createVideoAtGym(seedOwner, 1L, 1003L, true, "seed");

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seedVideoId)
                    .param("lat", "91")
                    .param("lng", "127.0200"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seedVideoId)
                    .param("lat", "37.5000")
                    .param("lng", "181"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seedVideoId)
                    .param("lat", "37.5000")
                    .param("lng", "127.0200")
                    .param("radius", "NaN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("C001"));
}
```

- [ ] **Step 8: Add optional favorite test**

Add this test:

```java
@Test
@DisplayName("영상 기반 암장 추천 — 인증 사용자가 호출하면 즐겨찾기 여부를 반영한다")
void getVideoGymRecommendations_withTokenReturnsFavorite() throws Exception {
    String seedOwner = register("seed-favorite@hola.com", "seedfavorite");
    String viewer = register("viewer-favorite@hola.com", "viewerfavorite");
    long viewerId = userMapper.findByEmail("viewer-favorite@hola.com").getId();
    jdbcTemplate.update("INSERT INTO favorites (user_id, gym_id) VALUES (?, ?)", viewerId, 1L);

    long seedVideoId = createVideoAtGym(seedOwner, 1L, 1003L, true, "seed-no-analysis");

    mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seedVideoId)
                    .param("lat", "37.5000")
                    .param("lng", "127.0200")
                    .header("Authorization", "Bearer " + viewer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].isFavorite").value(true));
}
```

- [ ] **Step 9: Add helper methods**

Add these helpers near the existing helper methods:

```java
private long createVideoAtGym(String token, Long gymId, Long gymGradeId, boolean isPublic, String title)
        throws Exception {
    long userId = dataOf(mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())).path("userId").asLong();
    String path = "videos/uploads/" + userId + "/" + java.util.UUID.randomUUID() + ".mp4";
    String thumbnailPath = "videos/thumbnails/" + userId + "/" + java.util.UUID.randomUUID() + ".jpg";
    var request = new CreateVideoRequest(gymId, title, "desc", gymGradeId,
            path, thumbnailPath, 30, RECORDED_DATE, isPublic);
    return dataOf(mockMvc.perform(post("/api/videos")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated()))
            .path("id")
            .asLong();
}

private void insertVideoAnalysis(long videoId, List<String> techniques, Boolean isDynamic) throws Exception {
    String json = objectMapper.writeValueAsString(techniques);
    jdbcTemplate.update("""
            INSERT INTO analysis_video_results (
                video_id, model_version, ai_techniques, ai_is_dynamic, ai_dynamic_probability,
                final_techniques, final_is_dynamic, feedback_applied
            )
            VALUES (?, 'test-model', ?::jsonb, ?, 0.90, ?::jsonb, ?, false)
            """, videoId, json, isDynamic, json, isDynamic);
}
```

- [ ] **Step 10: Run the new tests and verify they fail for missing endpoint/classes**

Run:

```bash
./mvnw -Dtest=VideoIntegrationTest test
```

Expected before implementation:

```text
BUILD FAILURE
```

The failure should be one of these:

```text
No handler found for GET /api/videos/{videoId}/recommendations/gyms
```

or assertions showing 404/405 instead of the expected response.

---

### Task 3: Add Response DTO And Domain Projection

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/video/domain/VideoRecommendedGym.java`
- Create: `src/main/java/com/holaclimbing/server/domain/video/dto/response/VideoRecommendedGymResponse.java`

- [ ] **Step 1: Create the domain projection**

Create `src/main/java/com/holaclimbing/server/domain/video/domain/VideoRecommendedGym.java`:

```java
package com.holaclimbing.server.domain.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoRecommendedGym {

    private Long id;
    private String name;
    private String address;
    private String thumbnailUrl;
    private String businessHours;
    private String regionCode;
    private BigDecimal ratingAvg;
    private int ratingCount;
    private Boolean favorite;
    private Double distanceKm;
    private Double similarityScore;
    private Double techniqueScore;
    private Double dynamicScore;
    private Double locationRatingScore;
    private Integer analyzedVideoCount;
    private String source;
}
```

- [ ] **Step 2: Create the response DTO**

Create `src/main/java/com/holaclimbing/server/domain/video/dto/response/VideoRecommendedGymResponse.java`:

```java
package com.holaclimbing.server.domain.video.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import com.holaclimbing.server.domain.video.domain.VideoRecommendedGym;

import java.math.BigDecimal;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VideoRecommendedGymResponse(
        Long id,
        String name,
        String address,
        String thumbnailUrl,
        String regionCode,
        BigDecimal ratingAvg,
        int ratingCount,
        Map<String, DayHours> businessHours,
        boolean isOpen,
        boolean isFavorite,
        Double distanceKm,
        Double similarityScore,
        Double techniqueScore,
        Double dynamicScore,
        Double locationRatingScore,
        Integer analyzedVideoCount,
        String source
) {
    public static VideoRecommendedGymResponse from(VideoRecommendedGym gym,
                                                   String thumbnailUrl,
                                                   Map<String, DayHours> businessHours,
                                                   boolean isOpen,
                                                   boolean isFavorite) {
        return new VideoRecommendedGymResponse(
                gym.getId(),
                gym.getName(),
                gym.getAddress(),
                thumbnailUrl,
                gym.getRegionCode(),
                gym.getRatingAvg(),
                gym.getRatingCount(),
                businessHours,
                isOpen,
                isFavorite,
                gym.getDistanceKm(),
                gym.getSimilarityScore(),
                gym.getTechniqueScore(),
                gym.getDynamicScore(),
                gym.getLocationRatingScore(),
                gym.getAnalyzedVideoCount(),
                gym.getSource()
        );
    }
}
```

- [ ] **Step 3: Compile**

Run:

```bash
./mvnw -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit projection and DTO**

```bash
git add src/main/java/com/holaclimbing/server/domain/video/domain/VideoRecommendedGym.java \
  src/main/java/com/holaclimbing/server/domain/video/dto/response/VideoRecommendedGymResponse.java
git commit -m "feat(video): add gym recommendation response types"
```

---

### Task 4: Add Video Gym Recommendation Mapper

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/video/mapper/VideoGymRecommendationMapper.java`
- Create: `src/main/resources/mapper/video/VideoGymRecommendationMapper.xml`

- [ ] **Step 1: Create mapper interface**

Create `src/main/java/com/holaclimbing/server/domain/video/mapper/VideoGymRecommendationMapper.java`:

```java
package com.holaclimbing.server.domain.video.mapper;

import com.holaclimbing.server.domain.analysis.domain.AnalysisVideoResult;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.domain.VideoRecommendedGym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VideoGymRecommendationMapper {

    Video findSeedVideo(Long videoId);

    AnalysisVideoResult findSeedAnalysis(Long videoId);

    List<VideoRecommendedGym> findSimilarGyms(@Param("videoId") Long videoId,
                                              @Param("viewerId") Long viewerId,
                                              @Param("lat") double lat,
                                              @Param("lng") double lng,
                                              @Param("radiusKm") double radiusKm,
                                              @Param("limit") int limit,
                                              @Param("minAnalyzedVideos") int minAnalyzedVideos,
                                              @Param("techniqueWeight") double techniqueWeight,
                                              @Param("dynamicWeight") double dynamicWeight,
                                              @Param("locationRatingWeight") double locationRatingWeight);

    List<VideoRecommendedGym> findNearbyGyms(@Param("viewerId") Long viewerId,
                                             @Param("lat") double lat,
                                             @Param("lng") double lng,
                                             @Param("radiusKm") double radiusKm,
                                             @Param("limit") int limit,
                                             @Param("locationRatingWeight") double locationRatingWeight);
}
```

- [ ] **Step 2: Create mapper XML**

Create `src/main/resources/mapper/video/VideoGymRecommendationMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.holaclimbing.server.domain.video.mapper.VideoGymRecommendationMapper">

    <select id="findSeedVideo" resultType="com.holaclimbing.server.domain.video.domain.Video">
        SELECT videos.id, videos.user_id, videos.gym_id, videos.gym_grade_id,
               u.nickname,
               u.profile_image,
               g.name AS gym_name,
               gg.label AS gym_grade_label,
               gg.difficulty_order AS gym_grade_difficulty_order,
               videos.title, videos.description,
               videos.gcs_path, videos.gcs_streaming_path, videos.thumbnail_path,
               videos.duration_seconds, videos.recorded_date, videos.file_size_bytes, videos.mime_type,
               videos.view_count, videos.like_count, videos.comment_count, videos.status, videos.is_public,
               videos.created_at, videos.updated_at, videos.deleted_at
        FROM videos
        JOIN users u ON u.id = videos.user_id
        JOIN gyms g ON g.id = videos.gym_id
        JOIN gym_grades gg ON gg.id = videos.gym_grade_id AND gg.gym_id = videos.gym_id
        WHERE videos.id = #{videoId}
          AND videos.deleted_at IS NULL
    </select>

    <select id="findSeedAnalysis"
            resultType="com.holaclimbing.server.domain.analysis.domain.AnalysisVideoResult">
        SELECT video_id, model_version,
               ai_techniques::text AS ai_techniques,
               ai_is_dynamic,
               ai_dynamic_probability,
               final_techniques::text AS final_techniques,
               final_is_dynamic,
               feedback_applied,
               feedback_note,
               corrected_by,
               corrected_at,
               created_at,
               updated_at
        FROM analysis_video_results
        WHERE video_id = #{videoId}
    </select>

    <sql id="candidateGyms">
        SELECT g.id, g.name, g.address, g.thumbnail_url, g.business_hours, g.region_code,
               g.rating_avg, g.rating_count,
               EXISTS (
                   SELECT 1
                   FROM favorites f
                   WHERE #{viewerId} IS NOT NULL
                     AND f.user_id = #{viewerId}
                     AND f.gym_id = g.id
               ) AS favorite,
               6371 * acos(GREATEST(-1.0, LEAST(1.0,
                   cos(radians(#{lat})) * cos(radians(g.lat))
                   * cos(radians(g.lng) - radians(#{lng}))
                   + sin(radians(#{lat})) * sin(radians(g.lat))
               ))) AS distance_km
        FROM gyms g
        WHERE g.lat IS NOT NULL
          AND g.lng IS NOT NULL
          AND g.status = 'active'
          AND g.deleted_at IS NULL
    </sql>

    <select id="findSimilarGyms" resultType="com.holaclimbing.server.domain.video.domain.VideoRecommendedGym">
        WITH seed AS (
            SELECT final_techniques, final_is_dynamic
            FROM analysis_video_results
            WHERE video_id = #{videoId}
        ),
        seed_techniques AS (
            SELECT technique
            FROM seed
            CROSS JOIN LATERAL jsonb_array_elements_text(
                COALESCE(seed.final_techniques, '[]'::jsonb)
            ) AS t(technique)
        ),
        seed_total AS (
            SELECT COUNT(*)::DOUBLE PRECISION AS technique_count
            FROM seed_techniques
        ),
        candidate AS (
            <include refid="candidateGyms"/>
        ),
        gym_analysis AS (
            SELECT c.id AS gym_id,
                   COUNT(DISTINCT v.id)::INTEGER AS analyzed_video_count,
                   COUNT(DISTINCT CASE
                       WHEN EXISTS (
                           SELECT 1
                           FROM jsonb_array_elements_text(COALESCE(r.final_techniques, '[]'::jsonb)) AS gt(technique)
                           JOIN seed_techniques st ON st.technique = gt.technique
                       )
                       THEN v.id
                   END)::DOUBLE PRECISION AS videos_with_any_match,
                   COUNT(DISTINCT gt.technique)::DOUBLE PRECISION AS matched_technique_count,
                   AVG(CASE
                       WHEN (SELECT final_is_dynamic FROM seed) IS NULL THEN 1.0
                       WHEN r.final_is_dynamic = (SELECT final_is_dynamic FROM seed) THEN 1.0
                       ELSE 0.0
                   END)::DOUBLE PRECISION AS dynamic_match_ratio
            FROM candidate c
            JOIN videos v ON v.gym_id = c.id
                         AND v.deleted_at IS NULL
                         AND v.is_public = TRUE
            JOIN analysis_video_results r ON r.video_id = v.id
            LEFT JOIN LATERAL jsonb_array_elements_text(COALESCE(r.final_techniques, '[]'::jsonb)) AS gt(technique)
                ON gt.technique IN (SELECT technique FROM seed_techniques)
            WHERE c.distance_km &lt;= #{radiusKm}
            GROUP BY c.id
        ),
        scored AS (
            SELECT c.id, c.name, c.address, c.thumbnail_url, c.business_hours, c.region_code,
                   c.rating_avg, c.rating_count, c.favorite, c.distance_km,
                   ga.analyzed_video_count,
                   (
                       CASE
                           WHEN st.technique_count = 0 THEN 0
                           ELSE LEAST(ga.matched_technique_count / st.technique_count, 1.0)
                       END
                   ) * #{techniqueWeight} AS technique_score,
                   COALESCE(ga.dynamic_match_ratio, 1.0) * #{dynamicWeight} AS dynamic_score,
                   (
                       (1.0 - LEAST(c.distance_km / #{radiusKm}, 1.0)) * 0.10
                       + LEAST(COALESCE(c.rating_avg, 0) / 5.0, 1.0) * 0.05
                   ) AS location_rating_score
            FROM candidate c
            JOIN gym_analysis ga ON ga.gym_id = c.id
            CROSS JOIN seed_total st
            WHERE c.distance_km &lt;= #{radiusKm}
              AND ga.analyzed_video_count &gt;= #{minAnalyzedVideos}
        )
        SELECT id, name, address, thumbnail_url, business_hours, region_code,
               rating_avg, rating_count, favorite, distance_km,
               (technique_score + dynamic_score + location_rating_score) AS similarity_score,
               technique_score, dynamic_score, location_rating_score,
               analyzed_video_count,
               'video_style_match' AS source
        FROM scored
        ORDER BY similarity_score DESC, distance_km ASC, rating_avg DESC, rating_count DESC, id ASC
        LIMIT #{limit}
    </select>

    <select id="findNearbyGyms" resultType="com.holaclimbing.server.domain.video.domain.VideoRecommendedGym">
        WITH candidate AS (
            <include refid="candidateGyms"/>
        )
        SELECT id, name, address, thumbnail_url, business_hours, region_code,
               rating_avg, rating_count, favorite, distance_km,
               NULL::DOUBLE PRECISION AS similarity_score,
               NULL::DOUBLE PRECISION AS technique_score,
               NULL::DOUBLE PRECISION AS dynamic_score,
               (
                   (1.0 - LEAST(distance_km / #{radiusKm}, 1.0)) * 0.10
                   + LEAST(COALESCE(rating_avg, 0) / 5.0, 1.0) * 0.05
               ) AS location_rating_score,
               NULL::INTEGER AS analyzed_video_count,
               'nearby' AS source
        FROM candidate
        WHERE distance_km &lt;= #{radiusKm}
        ORDER BY distance_km ASC, rating_avg DESC, rating_count DESC, id ASC
        LIMIT #{limit}
    </select>

</mapper>
```

- [ ] **Step 3: Run mapper compilation check**

Run:

```bash
./mvnw -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit mapper**

```bash
git add src/main/java/com/holaclimbing/server/domain/video/mapper/VideoGymRecommendationMapper.java \
  src/main/resources/mapper/video/VideoGymRecommendationMapper.xml
git commit -m "feat(video): add gym recommendation mapper"
```

---

### Task 5: Add Video Gym Recommendation Service

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationService.java`
- Create: `src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationServiceImpl.java`

- [ ] **Step 1: Create service interface**

Create `src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationService.java`:

```java
package com.holaclimbing.server.domain.video.service;

import com.holaclimbing.server.domain.video.dto.response.VideoRecommendedGymResponse;

import java.util.List;

public interface VideoGymRecommendationService {

    List<VideoRecommendedGymResponse> getRecommendedGyms(Long videoId,
                                                         Long viewerId,
                                                         double lat,
                                                         double lng,
                                                         double radiusKm,
                                                         int size);
}
```

- [ ] **Step 2: Create service implementation**

Create `src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationServiceImpl.java`:

```java
package com.holaclimbing.server.domain.video.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.analysis.domain.AnalysisVideoResult;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import com.holaclimbing.server.domain.gym.service.GymOperatingStatusResolver;
import com.holaclimbing.server.domain.gym.service.GymProfileImageUrlResolver;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.domain.VideoRecommendedGym;
import com.holaclimbing.server.domain.video.dto.response.VideoRecommendedGymResponse;
import com.holaclimbing.server.domain.video.mapper.VideoGymRecommendationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VideoGymRecommendationServiceImpl implements VideoGymRecommendationService {

    private static final int MIN_ANALYZED_VIDEOS_PER_GYM = 2;
    private static final double TECHNIQUE_WEIGHT = 0.70;
    private static final double DYNAMIC_WEIGHT = 0.15;
    private static final double LOCATION_RATING_WEIGHT = 0.15;
    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final VideoGymRecommendationMapper recommendationMapper;
    private final GymProfileImageUrlResolver profileImageUrlResolver;
    private final GymOperatingStatusResolver operatingStatusResolver;
    private final ObjectMapper objectMapper;

    @Override
    public List<VideoRecommendedGymResponse> getRecommendedGyms(Long videoId,
                                                                Long viewerId,
                                                                double lat,
                                                                double lng,
                                                                double radiusKm,
                                                                int size) {
        validateRequest(lat, lng, radiusKm);
        Video seedVideo = recommendationMapper.findSeedVideo(videoId);
        if (seedVideo == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        if (!seedVideo.isPublic()) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_ACCESSIBLE);
        }

        AnalysisVideoResult seedAnalysis = recommendationMapper.findSeedAnalysis(videoId);
        if (seedAnalysis == null || parseFinalTechniques(seedAnalysis).isEmpty()) {
            return toResponses(findNearby(viewerId, lat, lng, radiusKm, size));
        }

        List<VideoRecommendedGym> similarGyms = recommendationMapper.findSimilarGyms(
                videoId,
                viewerId,
                lat,
                lng,
                radiusKm,
                size,
                MIN_ANALYZED_VIDEOS_PER_GYM,
                TECHNIQUE_WEIGHT,
                DYNAMIC_WEIGHT,
                LOCATION_RATING_WEIGHT);
        if (similarGyms.isEmpty()) {
            return toResponses(findNearby(viewerId, lat, lng, radiusKm, size));
        }
        return toResponses(similarGyms);
    }

    private List<VideoRecommendedGym> findNearby(Long viewerId, double lat, double lng, double radiusKm, int size) {
        return recommendationMapper.findNearbyGyms(viewerId, lat, lng, radiusKm, size, LOCATION_RATING_WEIGHT);
    }

    private List<String> parseFinalTechniques(AnalysisVideoResult result) {
        String json = result.getFinalTechniques();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "영상 분석 기술 정보가 올바르지 않습니다.");
        }
    }

    private List<VideoRecommendedGymResponse> toResponses(List<VideoRecommendedGym> gyms) {
        return gyms.stream()
                .map(gym -> {
                    Map<String, DayHours> businessHours =
                            operatingStatusResolver.parseBusinessHours(gym.getBusinessHours());
                    return VideoRecommendedGymResponse.from(
                            gym,
                            profileImageUrlResolver.resolve(gym.getThumbnailUrl()),
                            businessHours,
                            operatingStatusResolver.isOpenNow(businessHours),
                            Boolean.TRUE.equals(gym.getFavorite()));
                })
                .toList();
    }

    private void validateRequest(double lat, double lng, double radiusKm) {
        if (!Double.isFinite(lat) || lat < MIN_LAT || lat > MAX_LAT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "위도는 -90~90 범위여야 합니다.");
        }
        if (!Double.isFinite(lng) || lng < MIN_LNG || lng > MAX_LNG) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "경도는 -180~180 범위여야 합니다.");
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반경은 0보다 커야 합니다.");
        }
    }
}
```

- [ ] **Step 3: Compile**

Run:

```bash
./mvnw -DskipTests compile
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit service**

```bash
git add src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationService.java \
  src/main/java/com/holaclimbing/server/domain/video/service/VideoGymRecommendationServiceImpl.java
git commit -m "feat(video): add gym recommendation service"
```

---

### Task 6: Add Video Recommendation Controller

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/video/VideoRecommendationController.java`

- [ ] **Step 1: Create controller**

Create `src/main/java/com/holaclimbing/server/domain/video/VideoRecommendationController.java`:

```java
package com.holaclimbing.server.domain.video;

import static com.holaclimbing.server.common.exception.error.ErrorCode.INVALID_INPUT;
import static com.holaclimbing.server.common.exception.error.ErrorCode.VIDEO_NOT_ACCESSIBLE;
import static com.holaclimbing.server.common.exception.error.ErrorCode.VIDEO_NOT_FOUND;

import com.holaclimbing.server.common.exception.docs.ApiErrorCodes;
import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoRecommendedGymResponse;
import com.holaclimbing.server.domain.video.service.VideoGymRecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/videos/{videoId}/recommendations")
@RequiredArgsConstructor
@Validated
public class VideoRecommendationController {

    private final VideoGymRecommendationService videoGymRecommendationService;

    @ApiErrorCodes({VIDEO_NOT_FOUND, VIDEO_NOT_ACCESSIBLE, INVALID_INPUT})
    @GetMapping("/gyms")
    public ApiResponse<List<VideoRecommendedGymResponse>> getRecommendedGyms(
            @PathVariable Long videoId,
            @AuthenticationPrincipal Long viewerId,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") @Positive double radius,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(videoGymRecommendationService.getRecommendedGyms(
                videoId, viewerId, lat, lng, radius, size));
    }
}
```

- [ ] **Step 2: Confirm SecurityConfig needs no change**

Do not modify `SecurityConfig`. The existing rule:

```java
.requestMatchers(HttpMethod.GET,
        "/api/videos/**",
        "/api/gyms/**").permitAll()
```

already makes this new endpoint public. `@AuthenticationPrincipal Long viewerId` remains nullable for anonymous calls.

- [ ] **Step 3: Run the target integration tests**

Run:

```bash
./mvnw -Dtest=VideoIntegrationTest test
```

Expected after Tasks 3-6:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Commit controller**

```bash
git add src/main/java/com/holaclimbing/server/domain/video/VideoRecommendationController.java \
  src/test/java/com/holaclimbing/server/domain/video/VideoIntegrationTest.java
git commit -m "feat(video): expose gym recommendations for a video"
```

---

### Task 7: Tighten SQL Semantics And Performance

**Files:**
- Modify: `src/main/resources/mapper/video/VideoGymRecommendationMapper.xml`
- Optional Create: `src/main/resources/db/migration/V12__video_gym_recommendation_indexes.sql`

- [ ] **Step 1: Run an EXPLAIN smoke on representative data**

If a local perf DB exists, run an EXPLAIN against the `findSimilarGyms` SQL with:

```bash
/opt/homebrew/opt/libpq/bin/psql "$DATABASE_URL" -c "EXPLAIN (ANALYZE, BUFFERS) SELECT 1"
```

Expected:

```text
QUERY PLAN
...
```

If no local perf DB exists, skip only this EXPLAIN step and rely on integration tests.

- [ ] **Step 2: Add support indexes only if EXPLAIN shows sequential scans becoming a bottleneck**

Create `src/main/resources/db/migration/V12__video_gym_recommendation_indexes.sql` only if needed:

```sql
CREATE INDEX IF NOT EXISTS idx_analysis_video_results_final_techniques_gin
    ON analysis_video_results USING gin (final_techniques);

CREATE INDEX IF NOT EXISTS idx_videos_public_gym_analysis
    ON videos(gym_id, id)
    WHERE is_public = TRUE AND deleted_at IS NULL;
```

Do not add the migration if the current dataset and query plan are already acceptable. This API has small candidate sets after radius filtering, so the index may be unnecessary for the final project scope.

- [ ] **Step 3: Run migration test if a migration is added**

Run:

```bash
./mvnw -Dtest=FlywayMigrationIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Run target and recommendation regression tests**

Run:

```bash
./mvnw -Dtest=VideoIntegrationTest,RecommendationIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit SQL/index refinements**

If only mapper SQL changed:

```bash
git add src/main/resources/mapper/video/VideoGymRecommendationMapper.xml
git commit -m "fix(video): tune gym recommendation ranking query"
```

If a migration was added:

```bash
git add src/main/resources/mapper/video/VideoGymRecommendationMapper.xml \
  src/main/resources/db/migration/V12__video_gym_recommendation_indexes.sql
git commit -m "perf(video): add gym recommendation support indexes"
```

---

### Task 8: Documentation And Final Verification

**Files:**
- Modify: `README.md`
- Optional Modify: API spec source if this project still syncs with the external Notion/API sheet.

- [ ] **Step 1: Update README feature list**

In `README.md`, under the video or recommendation feature description, add:

```markdown
- **영상 기반 암장 추천** — 공개 영상의 대표 분석 결과(`final_techniques`, `final_is_dynamic`)를 기준으로 반경 내 암장의 공개 분석 영상 분포와 비교해 유사한 암장을 추천합니다. 분석 데이터가 부족하면 거리/평점 기반 주변 암장으로 fallback합니다.
```

- [ ] **Step 2: Run formatting and diff checks**

Run:

```bash
git diff --check
```

Expected:

```text
```

No output.

- [ ] **Step 3: Run target tests**

Run:

```bash
./mvnw -Dtest=VideoIntegrationTest,RecommendationIntegrationTest,FlywayMigrationIntegrationTest test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Run full test suite if time allows**

Run:

```bash
./mvnw test
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Commit docs**

```bash
git add README.md
git commit -m "docs(video): describe video-based gym recommendations"
```

---

## Self-Review

### Spec Coverage

- Public endpoint path is covered by Task 6.
- All new implementation files stay under `domain/video` and `mapper/video`, covered by Tasks 3-6.
- Existing `domain/recommendation` APIs are untouched.
- Seed data uses `analysis_video_results.final_techniques` and `final_is_dynamic`, covered by Task 4 SQL and Task 5 service fallback.
- Gym candidate data uses public analyzed videos only, covered by `JOIN videos v ... v.is_public = TRUE AND v.deleted_at IS NULL` in Task 4.
- Data scarcity fallback is covered by Task 5 service and Task 2 fallback tests.
- Public access is covered by SecurityConfig no-change note and tests that call without token.
- Optional favorite for authenticated callers is covered by Task 2 favorite test and Task 4 SQL.

### Placeholder Scan

- No unresolved placeholder markers are present.
- Optional branches are explicit: index migration is only created if EXPLAIN shows need; otherwise it is skipped intentionally.
- Each code-changing step includes concrete code.

### Type Consistency

- Controller calls `VideoGymRecommendationService.getRecommendedGyms(...)`.
- Service uses `VideoGymRecommendationMapper.findSeedVideo`, `findSeedAnalysis`, `findSimilarGyms`, and `findNearbyGyms`, all defined in Task 4.
- Mapper XML result type is `VideoRecommendedGym`, defined in Task 3.
- Response DTO consumes `VideoRecommendedGym`, also defined in Task 3.
- Test response field names match `VideoRecommendedGymResponse` record component names.

### Risk Notes

- Missing seed video returns `V001`, and private seed video returns `V006`. The mapper fetches active videos regardless of `is_public`, and the service enforces public accessibility.
- The Task 4 SQL calculates technique similarity by unique technique coverage, not per-segment frequency. This matches V1's `analysis_video_results.final_techniques` data shape.
- The SQL excludes private candidate videos even when the caller is the owner. This is intentional because the API is public and should recommend from public community data only.
