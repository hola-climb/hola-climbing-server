package com.holaclimbing.server.domain.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import static com.holaclimbing.server.TestSignupRequests.signupRequest;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stats 도메인(클라이밍 통계 조회) 통합 테스트.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/terms-data.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/climbing-logs-schema.sql",
        "classpath:sql/videos-schema.sql",
        "classpath:sql/analysis-schema.sql",
        "classpath:sql/stats-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StatsIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("내 통계 — 분석 데이터가 있으면 동작별 횟수까지 반환한다")
    void getMyStats_withData() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedVideoResult(seedVideo(userId, 500), true, "[\"high_step\", \"flagging\"]");
        seedVideoResult(seedVideo(userId, 700), false, "[\"high_step\"]");

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.totalVideos").value(2))
                .andExpect(jsonPath("$.data.totalClimbingSeconds").value(1200))
                .andExpect(jsonPath("$.data.techniqueCounts.high_step").value(2))
                .andExpect(jsonPath("$.data.techniqueCounts.flagging").value(1));
    }

    @Test
    @DisplayName("내 통계 — 분석 데이터가 없으면 0으로 채운 통계를 반환한다 (dynamic/static 모두 0)")
    void getMyStats_noData_returnsZeros() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVideos").value(0))
                .andExpect(jsonPath("$.data.totalClimbingSeconds").value(0))
                .andExpect(jsonPath("$.data.techniqueCounts").isEmpty())
                .andExpect(jsonPath("$.data.dynamicCount").value(0))
                .andExpect(jsonPath("$.data.staticCount").value(0))
                .andExpect(jsonPath("$.data.isDynamic").value(false));
    }

    @Test
    @DisplayName("내 통계 — user_stats 캐시가 없어도 실제 업로드 영상 수와 재생시간을 집계한다")
    void getMyStats_countsUploadedVideosWithoutCachedStats() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedVideo(userId, 90);
        seedVideo(userId, 150);
        long deletedVideo = seedVideo(userId, 60);
        jdbcTemplate.update("UPDATE videos SET deleted_at = NOW() WHERE id = ?", deletedVideo);

        register("other@hola.com", "other");
        long otherId = userMapper.findByEmail("other@hola.com").getId();
        seedVideo(otherId, 999);

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVideos").value(2))
                .andExpect(jsonPath("$.data.totalClimbingSeconds").value(240));
    }

    @Test
    @DisplayName("내 통계 — 내가 올린 모든 영상의 대표 분석 결과에서 dynamic/static 영상 개수가 집계된다")
    void getMyStats_dynamicAndStaticVideoCounts() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        // 영상 3개 등록 — 영상 단위 대표 결과 기준 dynamic 1, static 1, unknown 1
        long videoA = seedVideo(userId);
        long videoB = seedVideo(userId);
        long videoC = seedVideo(userId);
        seedVideoResult(videoA, true);
        seedVideoResult(videoB, false);
        seedVideoResult(videoC, null);
        // 다른 사용자의 영상 대표 결과는 집계에 포함되지 않아야 한다.
        register("other@hola.com", "other");
        long otherId = userMapper.findByEmail("other@hola.com").getId();
        long otherVideo = seedVideo(otherId);
        seedVideoResult(otherVideo, true);

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dynamicCount").value(1))
                .andExpect(jsonPath("$.data.staticCount").value(1))
                // 동률은 dynamic > static 이 아니므로 false
                .andExpect(jsonPath("$.data.isDynamic").value(false));
    }

    @Test
    @DisplayName("기술별 통계 — user_stats 캐시가 없어도 영상 대표 분석 결과의 final_techniques를 집계한다")
    void getTechniqueStats_countsFinalTechniquesWithoutCachedStats() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedVideoResult(seedVideo(userId), true, "[\"high_step\", \"dyno\"]");
        seedVideoResult(seedVideo(userId), false, "[\"dyno\"]");

        register("other@hola.com", "other");
        long otherId = userMapper.findByEmail("other@hola.com").getId();
        seedVideoResult(seedVideo(otherId), true, "[\"high_step\"]");

        mockMvc.perform(get("/api/stats/me/techniques").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.techniqueCounts.high_step").value(1))
                .andExpect(jsonPath("$.data.techniqueCounts.dyno").value(2))
                .andExpect(jsonPath("$.data.mostUsed").value("dyno"))
                .andExpect(jsonPath("$.data.leastUsed").value("high_step"));
    }

    @Test
    @DisplayName("특정 사용자 통계 — dynamic이 static보다 많으면 isDynamic=true")
    void getUserStats_isDynamic_true() throws Exception {
        register("dyn@hola.com", "dyn");
        long userId = userMapper.findByEmail("dyn@hola.com").getId();
        seedVideoResult(seedVideo(userId), true);
        seedVideoResult(seedVideo(userId), true);
        seedVideoResult(seedVideo(userId), false);

        mockMvc.perform(get("/api/stats/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dynamicCount").value(2))
                .andExpect(jsonPath("$.data.staticCount").value(1))
                .andExpect(jsonPath("$.data.isDynamic").value(true));
    }

    @Test
    @DisplayName("특정 사용자 통계 — static이 dynamic보다 많으면 isDynamic=false")
    void getUserStats_isDynamic_false() throws Exception {
        register("sta@hola.com", "sta");
        long userId = userMapper.findByEmail("sta@hola.com").getId();
        seedVideoResult(seedVideo(userId), false);
        seedVideoResult(seedVideo(userId), false);
        seedVideoResult(seedVideo(userId), true);

        mockMvc.perform(get("/api/stats/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dynamicCount").value(1))
                .andExpect(jsonPath("$.data.staticCount").value(2))
                .andExpect(jsonPath("$.data.isDynamic").value(false));
    }

    @Test
    @DisplayName("내 통계 — 토큰 없이 호출하면 401")
    void getMyStats_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/stats/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사용자 통계 — userId로 특정 사용자 통계를 조회한다")
    void getUserStats_byId_success() throws Exception {
        register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedVideoResult(seedVideo(userId, 200), true, "[\"dyno\"]");
        seedVideo(userId, 200);
        seedVideo(userId, 200);

        mockMvc.perform(get("/api/stats/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVideos").value(3))
                .andExpect(jsonPath("$.data.totalClimbingSeconds").value(600))
                .andExpect(jsonPath("$.data.techniqueCounts.dyno").value(1));
    }

    @Test
    @DisplayName("사용자 통계 — 없는 사용자는 404 U001")
    void getUserStats_nonexistentUser_returns404() throws Exception {
        mockMvc.perform(get("/api/stats/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("사용자 통계 — 정지된 사용자는 공개 통계에서 숨긴다")
    void getUserStats_suspendedUser_returns404() throws Exception {
        register("suspended-stats@hola.com", "suspendedstats");
        long userId = userMapper.findByEmail("suspended-stats@hola.com").getId();
        userMapper.updateStatus(userId, "SUSPENDED");

        mockMvc.perform(get("/api/stats/users/" + userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("기술별 통계 — 최다/최소 사용 기술을 함께 반환한다")
    void getTechniqueStats_withData() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedVideoResult(seedVideo(userId), true, "[\"high_step\", \"flagging\", \"dyno\"]");
        seedVideoResult(seedVideo(userId), true, "[\"high_step\", \"flagging\"]");
        seedVideoResult(seedVideo(userId), true, "[\"high_step\"]");

        mockMvc.perform(get("/api/stats/me/techniques").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.techniqueCounts.high_step").value(3))
                .andExpect(jsonPath("$.data.techniqueCounts.flagging").value(2))
                .andExpect(jsonPath("$.data.techniqueCounts.dyno").value(1))
                .andExpect(jsonPath("$.data.mostUsed").value("high_step"))
                .andExpect(jsonPath("$.data.leastUsed").value("dyno"));
    }

    @Test
    @DisplayName("기술별 통계 — 토큰 없이 호출하면 401")
    void getTechniqueStats_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/stats/me/techniques"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 암장 랭킹 — 월별 기록을 최빈순으로 조회하고 커서로 다음 랭킹을 가져온다")
    void getMyGymRankings_monthlyMostVisitedWithCursor() throws Exception {
        String token = register("ranking@hola.com", "ranking");
        long userId = userMapper.findByEmail("ranking@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 1));
        insertLog(userId, 1L, LocalDate.of(2026, 5, 8));
        insertLog(userId, 1L, LocalDate.of(2026, 5, 20));
        insertLog(userId, 2L, LocalDate.of(2026, 5, 2));
        insertLog(userId, 2L, LocalDate.of(2026, 5, 10));
        long deletedLogId = insertLog(userId, 2L, LocalDate.of(2026, 5, 30));
        jdbcTemplate.update("UPDATE climbing_logs SET deleted_at = NOW() WHERE id = ?", deletedLogId);
        insertLog(userId, 3L, LocalDate.of(2026, 5, 15));
        insertLog(userId, 4L, LocalDate.of(2026, 5, 16));
        insertLog(userId, 1L, LocalDate.of(2026, 4, 30));
        register("ranking-other@hola.com", "rankingother");
        long otherId = userMapper.findByEmail("ranking-other@hola.com").getId();
        insertLog(otherId, 2L, LocalDate.of(2026, 5, 31));

        ResultActions firstPage = mockMvc.perform(get("/api/stats/me/gyms/rankings")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("2026-05"))
                .andExpect(jsonPath("$.data.scope").value("monthly"))
                .andExpect(jsonPath("$.data.sort").value("mostVisited"))
                .andExpect(jsonPath("$.data.content[0].rank").value(1))
                .andExpect(jsonPath("$.data.content[0].gymId").value(1))
                .andExpect(jsonPath("$.data.content[0].gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.content[0].visitCount").value(3))
                .andExpect(jsonPath("$.data.content[0].latestVisitDate").value("2026-05-20"))
                .andExpect(jsonPath("$.data.content[1].rank").value(2))
                .andExpect(jsonPath("$.data.content[1].gymId").value(2))
                .andExpect(jsonPath("$.data.content[1].visitCount").value(2))
                .andExpect(jsonPath("$.data.content[1].latestVisitDate").value("2026-05-10"))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        String cursor = dataOf(firstPage).path("nextCursor").asText();
        mockMvc.perform(get("/api/stats/me/gyms/rankings")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05")
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rank").value(3))
                .andExpect(jsonPath("$.data.content[0].gymId").value(4))
                .andExpect(jsonPath("$.data.content[0].visitCount").value(1))
                .andExpect(jsonPath("$.data.content[0].latestVisitDate").value("2026-05-16"))
                .andExpect(jsonPath("$.data.content[1].rank").value(4))
                .andExpect(jsonPath("$.data.content[1].gymId").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("내 암장 랭킹 — month가 없으면 전체 기간 기록을 집계한다")
    void getMyGymRankings_allTimeWhenMonthMissing() throws Exception {
        String token = register("ranking-all@hola.com", "rankingall");
        long userId = userMapper.findByEmail("ranking-all@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 4, 1));
        insertLog(userId, 1L, LocalDate.of(2026, 5, 1));
        insertLog(userId, 2L, LocalDate.of(2026, 6, 1));

        mockMvc.perform(get("/api/stats/me/gyms/rankings")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").doesNotExist())
                .andExpect(jsonPath("$.data.scope").value("all"))
                .andExpect(jsonPath("$.data.content[0].gymId").value(1))
                .andExpect(jsonPath("$.data.content[0].visitCount").value(2))
                .andExpect(jsonPath("$.data.content[0].latestVisitDate").value("2026-05-01"))
                .andExpect(jsonPath("$.data.content[1].gymId").value(2))
                .andExpect(jsonPath("$.data.content[1].visitCount").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("내 암장 랭킹 — 기록이 없으면 빈 목록을 반환한다")
    void getMyGymRankings_noLogsReturnsEmptyPage() throws Exception {
        String token = register("ranking-empty@hola.com", "rankingempty");

        mockMvc.perform(get("/api/stats/me/gyms/rankings")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("2026-05"))
                .andExpect(jsonPath("$.data.scope").value("monthly"))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("내 암장 랭킹 — 잘못된 month는 T003을 반환한다")
    void getMyGymRankings_invalidMonthReturns400() throws Exception {
        String token = register("ranking-invalid@hola.com", "rankinginvalid");

        mockMvc.perform(get("/api/stats/me/gyms/rankings")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("T003"));
    }

    // ===== helpers =====

    private long insertLog(long userId, long gymId, LocalDate climbedOn) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO climbing_logs (user_id, gym_id, climbed_on, grade_counts)
                        VALUES (?, ?, ?, '{}'::jsonb)
                        RETURNING id
                        """,
                Long.class, userId, gymId, climbedOn);
    }

    private long seedVideo(long userId) {
        return seedVideo(userId, null);
    }

    private long seedVideo(long userId, Integer durationSeconds) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO videos (user_id, gym_id, gym_grade_id, title, gcs_path, duration_seconds, recorded_date, status, is_public) "
                        + "VALUES (?, 1, 1003, 'seed', 'seed/path.mp4', ?, DATE '2026-06-03', 'done', TRUE) RETURNING id",
                Long.class, userId, durationSeconds);
    }

    private void seedVideoResult(long videoId, Boolean isDynamic) {
        seedVideoResult(videoId, isDynamic, "[\"high_step\"]");
    }

    private void seedVideoResult(long videoId, Boolean isDynamic, String finalTechniquesJson) {
        jdbcTemplate.update(
                """
                        INSERT INTO analysis_video_results (
                            video_id, model_version, ai_techniques, ai_is_dynamic,
                            ai_dynamic_probability, final_techniques, final_is_dynamic, feedback_applied
                        )
                        VALUES (?, 'rule_v3', ?::jsonb, ?, 0.5, ?::jsonb, ?, FALSE)
                        """,
                videoId, finalTechniquesJson, isDynamic, finalTechniquesJson, isDynamic);
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());
        return dataOf(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD)))))
                .path("accessToken").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
