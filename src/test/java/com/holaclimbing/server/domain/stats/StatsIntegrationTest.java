package com.holaclimbing.server.domain.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
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
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
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
        seedStats(userId, 5, 1200L, "{\"highstep\":12,\"flagging\":8}");

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.totalVideos").value(5))
                .andExpect(jsonPath("$.data.totalClimbingSeconds").value(1200))
                .andExpect(jsonPath("$.data.techniqueCounts.highstep").value(12))
                .andExpect(jsonPath("$.data.techniqueCounts.flagging").value(8));
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
        seedStats(userId, 3, 600L, "{\"dyno\":4}");

        mockMvc.perform(get("/api/stats/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalVideos").value(3))
                .andExpect(jsonPath("$.data.techniqueCounts.dyno").value(4));
    }

    @Test
    @DisplayName("사용자 통계 — 없는 사용자는 404 U001")
    void getUserStats_nonexistentUser_returns404() throws Exception {
        mockMvc.perform(get("/api/stats/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("기술별 통계 — 최다/최소 사용 기술을 함께 반환한다")
    void getTechniqueStats_withData() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedStats(userId, 5, 1200L, "{\"highstep\":12,\"flagging\":8,\"dyno\":3}");

        mockMvc.perform(get("/api/stats/me/techniques").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.techniqueCounts.highstep").value(12))
                .andExpect(jsonPath("$.data.mostUsed").value("highstep"))
                .andExpect(jsonPath("$.data.leastUsed").value("dyno"));
    }

    @Test
    @DisplayName("기술별 통계 — 토큰 없이 호출하면 401")
    void getTechniqueStats_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/stats/me/techniques"))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private void seedStats(long userId, int totalVideos, long totalSeconds, String techniqueCountsJson) {
        jdbcTemplate.update(
                "INSERT INTO user_stats (user_id, total_videos, total_climbing_seconds, technique_counts) "
                        + "VALUES (?, ?, ?, ?::jsonb)",
                userId, totalVideos, totalSeconds, techniqueCountsJson);
    }

    private long seedVideo(long userId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO videos (user_id, gym_id, gym_grade_id, title, gcs_path, recorded_date, status, is_public) "
                        + "VALUES (?, 1, 1003, 'seed', 'seed/path.mp4', DATE '2026-06-03', 'done', TRUE) RETURNING id",
                Long.class, userId);
    }

    private void seedVideoResult(long videoId, Boolean isDynamic) {
        jdbcTemplate.update(
                """
                        INSERT INTO analysis_video_results (
                            video_id, model_version, ai_techniques, ai_is_dynamic,
                            ai_dynamic_probability, final_techniques, final_is_dynamic, feedback_applied
                        )
                        VALUES (?, 'rule_v3', '["high_step"]'::jsonb, ?, 0.5, '["high_step"]'::jsonb, ?, FALSE)
                        """,
                videoId, isDynamic, isDynamic);
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
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
