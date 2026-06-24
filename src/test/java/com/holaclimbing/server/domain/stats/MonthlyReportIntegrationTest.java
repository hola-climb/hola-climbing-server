package com.holaclimbing.server.domain.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import static com.holaclimbing.server.TestSignupRequests.signupRequest;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "app.monthly-report.generate-on-miss=true",
        "app.monthly-report.llm.mode=rule"
})
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
        "classpath:sql/monthly-reports-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MonthlyReportIntegrationTest {

    private static final String PASSWORD = "password123";

    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("월간 리포트 — 기록이 있으면 완등·세션·방문암장은 climbing_logs 기준으로 계산한다")
    void monthlyReport_prefersLogsForTotals() throws Exception {
        String token = register("monthly-log@hola.com", "monthlylog");
        long userId = userMapper.findByEmail("monthly-log@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("빨강", 4, "파랑", 2));
        insertLog(userId, 2L, LocalDate.of(2026, 5, 20), Map.of("노랑", 4));
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), true, "[\"dyno\"]");
        insertVideo(userId, 2L, 1004L, LocalDate.of(2026, 5, 20), false, "[\"flagging\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("insufficientData"))
                .andExpect(jsonPath("$.data.source").value("log"))
                .andExpect(jsonPath("$.data.metrics.sessions").value(2))
                .andExpect(jsonPath("$.data.metrics.problemsSolved").value(10))
                .andExpect(jsonPath("$.data.metrics.gymsVisited").value(2))
                .andExpect(jsonPath("$.data.metrics.videos").value(2));
    }

    @Test
    @DisplayName("월간 리포트 — 기록이 없으면 영상으로 세션·완등·방문암장을 fallback 추정한다")
    void monthlyReport_fallsBackToVideosWhenLogsAreMissing() throws Exception {
        String token = register("monthly-video@hola.com", "monthlyvideo");
        long userId = userMapper.findByEmail("monthly-video@hola.com").getId();
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), true, "[\"dyno\"]");
        insertVideo(userId, 1L, 1002L, LocalDate.of(2026, 5, 10), false, "[\"flagging\"]");
        insertVideo(userId, 2L, 1004L, LocalDate.of(2026, 5, 20), true, "[\"heel_hook\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("insufficientData"))
                .andExpect(jsonPath("$.data.source").value("videoFallback"))
                .andExpect(jsonPath("$.data.metrics.sessions").value(2))
                .andExpect(jsonPath("$.data.metrics.problemsSolved").value(3))
                .andExpect(jsonPath("$.data.metrics.gymsVisited").value(2));
    }

    @Test
    @DisplayName("월간 리포트 — 선택 암장 기준 최고난이도는 selected gym only + difficulty_order로 계산한다")
    void monthlyReport_gradeUsesSelectedGymOnly() throws Exception {
        String token = register("monthly-grade@hola.com", "monthlygrade");
        long userId = userMapper.findByEmail("monthly-grade@hola.com").getId();
        insertLog(userId, 3L, LocalDate.of(2026, 5, 10), Map.of("V3", 1, "V5", 1));
        insertLog(userId, 2L, LocalDate.of(2026, 5, 11), Map.of("노랑", 1));
        insertLog(userId, 3L, LocalDate.of(2026, 4, 20), Map.of("V4", 1));

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05")
                        .param("gymId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade.gymId").value(3))
                .andExpect(jsonPath("$.data.grade.maxGrade").value("V5"))
                .andExpect(jsonPath("$.data.grade.maxGradePrevMonth").value("V4"));
    }

    @Test
    @DisplayName("월간 리포트 — 선택 암장에 해당 월 기록과 영상이 없으면 grade를 생략한다")
    void monthlyReport_gradeIsNullWhenSelectedGymHasNoData() throws Exception {
        String token = register("monthly-no-grade@hola.com", "monthlynograde");
        long userId = userMapper.findByEmail("monthly-no-grade@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("빨강", 3));

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05")
                        .param("gymId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").doesNotExist());
    }

    @Test
    @DisplayName("월간 리포트 — dynamic/static과 기술 통계는 해당 월 모든 영상 분석 결과를 사용한다")
    void monthlyReport_usesAllMonthlyVideosForStyle() throws Exception {
        String token = register("monthly-style@hola.com", "monthlystyle");
        long userId = userMapper.findByEmail("monthly-style@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("빨강", 10));
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), true, "[\"dyno\", \"toe_hook\"]");
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 11), false, "[\"flagging\"]");
        insertVideo(userId, 2L, 1004L, LocalDate.of(2026, 5, 12), true, "[\"dyno\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.metrics.dynamicCount").value(2))
                .andExpect(jsonPath("$.data.metrics.staticCount").value(1))
                .andExpect(jsonPath("$.data.metrics.dynamicRatio").value(0.67))
                .andExpect(jsonPath("$.data.metrics.techniqueCounts.dyno").value(2))
                .andExpect(jsonPath("$.data.metrics.techniqueCounts.flagging").value(1));
    }

    @Test
    @DisplayName("월간 리포트 — 적게 사용한 기술은 팁·목표·공개 분석 영상 기반 추천 암장으로 이어진다")
    void monthlyReport_recommendsGymsForUnderusedTechniques() throws Exception {
        String token = register("monthly-recommend@hola.com", "monthlyrecommend");
        long userId = userMapper.findByEmail("monthly-recommend@hola.com").getId();
        insertLog(userId, 1L, LocalDate.of(2026, 5, 10), Map.of("빨강", 12));
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 11), true, "[\"dyno\"]");
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 12), true, "[\"dyno\"]");
        insertVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 13), true, "[\"dyno\"]");
        insertPublicTechniqueVideo(2L, 1004L, LocalDate.of(2026, 5, 12), false, "[\"heel_hook\", \"lock_off\"]");
        insertPublicTechniqueVideo(2L, 1005L, LocalDate.of(2026, 5, 13), false, "[\"heel_hook\"]");

        mockMvc.perform(get("/api/stats/me/monthly-reports")
                        .header("Authorization", "Bearer " + token)
                        .param("month", "2026-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.tip.type").value("underusedTechnique"))
                .andExpect(jsonPath("$.data.nextMonthGoal.metric").value("specificTechnique"))
                .andExpect(jsonPath("$.data.recommendedGyms[0].gymId").value(2));
    }

    private void insertLog(long userId, long gymId, LocalDate climbedOn, Map<String, Integer> gradeCounts)
            throws Exception {
        jdbcTemplate.update("""
                INSERT INTO climbing_logs (user_id, gym_id, climbed_on, grade_counts)
                VALUES (?, ?, ?, ?::jsonb)
                """,
                userId, gymId, climbedOn, objectMapper.writeValueAsString(gradeCounts));
    }

    private long insertVideo(long userId, long gymId, long gymGradeId, LocalDate recordedDate,
                             Boolean isDynamic, String techniquesJson) {
        long videoId = jdbcTemplate.queryForObject("""
                INSERT INTO videos (
                    user_id, gym_id, gym_grade_id, title, gcs_path,
                    duration_seconds, recorded_date, mime_type, status, is_public
                )
                VALUES (?, ?, ?, 'monthly report clip', ?, 60, ?, 'video/mp4', 'done', TRUE)
                RETURNING id
                """,
                Long.class,
                userId,
                gymId,
                gymGradeId,
                "videos/uploads/" + userId + "/monthly-report-" + sequence.incrementAndGet() + ".mp4",
                recordedDate);

        jdbcTemplate.update("""
                INSERT INTO analysis_video_results (
                    video_id, model_version, ai_techniques, ai_is_dynamic,
                    ai_dynamic_probability, final_techniques, final_is_dynamic, feedback_applied
                )
                VALUES (?, 'rule_v3', ?::jsonb, ?, 0.5, ?::jsonb, ?, FALSE)
                """,
                videoId, techniquesJson, isDynamic, techniquesJson, isDynamic);
        return videoId;
    }

    private void insertPublicTechniqueVideo(long gymId, long gymGradeId, LocalDate recordedDate,
                                            Boolean isDynamic, String techniquesJson) throws Exception {
        int suffix = sequence.incrementAndGet();
        register("monthly-candidate-" + suffix + "@hola.com", "candidate" + suffix);
        long candidateUserId = userMapper.findByEmail("monthly-candidate-" + suffix + "@hola.com").getId();
        insertVideo(candidateUserId, gymId, gymGradeId, recordedDate, isDynamic, techniquesJson);
    }

    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyEmailRequest(user.getEmailVerificationToken()))))
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
