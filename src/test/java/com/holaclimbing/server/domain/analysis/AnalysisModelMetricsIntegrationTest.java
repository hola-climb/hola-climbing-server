package com.holaclimbing.server.domain.analysis;

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

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/videos-schema.sql",
        "classpath:sql/analysis-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AnalysisModelMetricsIntegrationTest {

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
    @DisplayName("모델 정확도 통계 — 피드백이 반영된 영상의 ai/final 결과를 모델 버전별로 비교한다")
    void getModelMetrics_comparesAiAndFinalResults() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long ownerId = registerUser("owner@hola.com", "owner");

        long exactMatchVideo = seedVideo(ownerId);
        seedVideoResult(exactMatchVideo,
                "[\"high_step\", \"flagging\"]", true, 0.82,
                "[\"high_step\", \"flagging\"]", true,
                true, "rule_v3+flow_rf_v2");

        long correctedVideo = seedVideo(ownerId);
        seedVideoResult(correctedVideo,
                "[\"dyno\"]", false, 0.31,
                "[\"dyno\", \"coordination\"]", true,
                true, "rule_v3+flow_rf_v2");

        long ignoredNoFeedbackVideo = seedVideo(ownerId);
        seedVideoResult(ignoredNoFeedbackVideo,
                "[\"lock_off\"]", false, 0.21,
                "[\"lock_off\"]", false,
                false, "rule_v3+flow_rf_v2");

        mockMvc.perform(get("/api/admin/analysis/models/rule_v3+flow_rf_v2/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelVersion").value("rule_v3+flow_rf_v2"))
                .andExpect(jsonPath("$.data.feedbackCount").value(2))
                .andExpect(jsonPath("$.data.dynamicEvaluatedCount").value(2))
                .andExpect(jsonPath("$.data.dynamicAccuracy").value(0.5))
                .andExpect(jsonPath("$.data.techniqueExactMatchAccuracy").value(0.5))
                .andExpect(jsonPath("$.data.perTechnique.high_step.truePositive").value(1))
                .andExpect(jsonPath("$.data.perTechnique.high_step.trueNegative").value(1))
                .andExpect(jsonPath("$.data.perTechnique.coordination.falseNegative").value(1));
    }

    private void seedVideoResult(long videoId,
                                 String aiTechniques,
                                 Boolean aiIsDynamic,
                                 Double aiDynamicProbability,
                                 String finalTechniques,
                                 Boolean finalIsDynamic,
                                 boolean feedbackApplied,
                                 String modelVersion) {
        jdbcTemplate.update("""
                        INSERT INTO analysis_video_results (
                            video_id, model_version, ai_techniques, ai_is_dynamic, ai_dynamic_probability,
                            final_techniques, final_is_dynamic, feedback_applied
                        )
                        VALUES (?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?)
                        """,
                videoId, modelVersion, aiTechniques, aiIsDynamic, aiDynamicProbability,
                finalTechniques, finalIsDynamic, feedbackApplied);
    }

    private long seedVideo(long userId) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO videos (
                            user_id, gym_id, gym_grade_id, title, gcs_path, recorded_date, status, is_public
                        )
                        VALUES (?, 1, 1003, 'seed', 'seed/path.mp4',
                                DATE '2026-06-03', 'done', TRUE)
                        RETURNING id
                        """,
                Long.class, userId);
    }

    private String registerAndLoginAdmin() throws Exception {
        String email = "admin-metrics@hola.com";
        long userId = registerUser(email, "adminmetrics");
        userMapper.updateRole(userId, "ADMIN");
        return login(email);
    }

    private long registerUser(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());
        return user.getId();
    }

    private String login(String email) throws Exception {
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
