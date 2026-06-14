package com.holaclimbing.server.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisFeedbackRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisSegmentPayload;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Analysis 도메인 통합 테스트.
 * 분석 결과 조회와 AI 워커의 결과 수신(영상 상태 전환)을 검증한다.
 */
@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "ai.callback-secret=test-ai-callback-secret"
})
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
class AnalysisIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String AI_CALLBACK_SECRET = "test-ai-callback-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("분석 조회 — 분석 전 영상은 status=pending, 영상 대표 결과는 비어 있음")
    void getAnalysis_beforeAnalysis() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.techniques").isEmpty())
                .andExpect(jsonPath("$.data.feedbackApplied").value(false))
                .andExpect(jsonPath("$.data.segments").doesNotExist());
    }

    @Test
    @DisplayName("분석 조회 실패 — 비공개 영상 분석 결과는 타인이 조회할 수 없다")
    void getAnalysis_privateVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("결과 수신 — done 결과를 받으면 세그먼트 raw와 영상 대표 결과를 저장한다")
    void ingest_done_storesSegmentsAndVideoResult() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        String workerPayload = """
                {
                  "status": "done",
                  "model_version": "rule_v3+flow_rf_v2",
                  "segments": [
                    {
                      "sequence_index": 0,
                      "start_time_ms": 0,
                      "end_time_ms": 1000,
                      "technique": "high_step",
                      "is_dynamic": false,
                      "confidence": 0.91
                    },
                    {
                      "sequence_index": 1,
                      "start_time_ms": 1000,
                      "end_time_ms": 2000,
                      "technique": "flagging",
                      "is_dynamic": false,
                      "confidence": 0.85
                    }
                  ],
                  "techniques": ["high_step", "flagging"],
                  "is_dynamic": false,
                  "dynamic_probability": 0.18
                }
                """;

        mockMvc.perform(aiCallbackPost(videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workerPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.modelVersion").value("rule_v3+flow_rf_v2"))
                .andExpect(jsonPath("$.data.techniques[0]").value("high_step"))
                .andExpect(jsonPath("$.data.techniques[1]").value("flagging"))
                .andExpect(jsonPath("$.data.isDynamic").value(false))
                .andExpect(jsonPath("$.data.dynamicProbability").value(0.18))
                .andExpect(jsonPath("$.data.feedbackApplied").value(false))
                .andExpect(jsonPath("$.data.segments").doesNotExist());

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis"))
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.techniques.length()").value(2))
                .andExpect(jsonPath("$.data.segments").doesNotExist());

        Integer segmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM analysis_results WHERE video_id = ?",
                Integer.class, videoId);
        String aiTechniques = jdbcTemplate.queryForObject(
                "SELECT ai_techniques::text FROM analysis_video_results WHERE video_id = ?",
                String.class, videoId);
        Boolean aiIsDynamic = jdbcTemplate.queryForObject(
                "SELECT ai_is_dynamic FROM analysis_video_results WHERE video_id = ?",
                Boolean.class, videoId);

        org.assertj.core.api.Assertions.assertThat(segmentCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(aiTechniques).isEqualTo("[\"high_step\", \"flagging\"]");
        org.assertj.core.api.Assertions.assertThat(aiIsDynamic).isFalse();
    }

    @Test
    @DisplayName("결과 수신 — AI 워커 snake_case 영상 대표 JSON을 그대로 수신해 저장한다")
    void ingest_workerSnakeCasePayload_storesVideoResult() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        String workerPayload = """
                {
                  "status": "done",
                  "model_version": "rule_v1",
                  "segments": [
                    {
                      "sequence_index": 0,
                      "start_time_ms": 0,
                      "end_time_ms": 1000,
                      "technique": "highstep",
                      "is_dynamic": false,
                      "confidence": 0.91
                    }
                  ],
                  "techniques": ["high_step"],
                  "is_dynamic": true,
                  "dynamic_probability": 0.73
                }
                """;

        mockMvc.perform(aiCallbackPost(videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workerPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.modelVersion").value("rule_v1"))
                .andExpect(jsonPath("$.data.techniques[0]").value("high_step"))
                .andExpect(jsonPath("$.data.isDynamic").value(true))
                .andExpect(jsonPath("$.data.dynamicProbability").value(0.73))
                .andExpect(jsonPath("$.data.segments").doesNotExist());
    }

    @Test
    @DisplayName("결과 수신 — failed 결과를 받으면 영상 status=failed")
    void ingest_failed_setsStatusFailed() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("failed", null, null);
        mockMvc.perform(aiCallbackPost(videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.techniques").isEmpty())
                .andExpect(jsonPath("$.data.segments").doesNotExist());
    }

    @Test
    @DisplayName("결과 수신 — 재분석 시 기존 결과를 새 결과로 대체한다")
    void ingest_reanalysis_replacesPrevious() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        ingest(videoId, new AnalysisIngestRequest("done", "rule_v1", List.of(
                new AnalysisSegmentPayload(0, 0, 1000, "highstep", false, 0.9f),
                new AnalysisSegmentPayload(1, 1000, 2000, "flagging", false, 0.8f),
                new AnalysisSegmentPayload(2, 2000, 3000, "dyno", true, 0.7f)),
                List.of("high_step", "flagging", "dyno"), true, 0.72f));
        ingest(videoId, new AnalysisIngestRequest("done", "lstm_v1", List.of(
                new AnalysisSegmentPayload(0, 0, 1500, "lock_off", false, 0.95f)),
                List.of("lock_off"), false, 0.12f));

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis"))
                .andExpect(jsonPath("$.data.techniques.length()").value(1))
                .andExpect(jsonPath("$.data.modelVersion").value("lstm_v1"))
                .andExpect(jsonPath("$.data.techniques[0]").value("lock_off"))
                .andExpect(jsonPath("$.data.isDynamic").value(false))
                .andExpect(jsonPath("$.data.segments").doesNotExist());
    }

    @Test
    @DisplayName("결과 수신 — 잘못된 status는 400")
    void ingest_invalidStatus_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("weird", "rule_v1", List.of());
        mockMvc.perform(aiCallbackPost(videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("분석 조회 — 없는 영상은 404 V001")
    void getAnalysis_videoNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/videos/999999/analysis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
    }

    @Test
    @DisplayName("결과 수신 — 없는 영상은 404 V001")
    void ingest_videoNotFound_returns404() throws Exception {
        var request = new AnalysisIngestRequest("done", "rule_v1", List.of(
                new AnalysisSegmentPayload(0, 0, 1000, "highstep", false, 0.9f)));
        mockMvc.perform(aiCallbackPost(999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
    }

    @Test
    @DisplayName("결과 수신 보안 — 콜백 시크릿이 없으면 401")
    void ingest_withoutCallbackSecret_returns401() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("done", "rule_v1", List.of());
        mockMvc.perform(post("/api/analysis/videos/" + videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("결과 수신 보안 — 올바른 콜백 시크릿이면 성공")
    void ingest_withCallbackSecret_returnsOk() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("done", "rule_v1", List.of());
        mockMvc.perform(aiCallbackPost(videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"));
    }

    @Test
    @DisplayName("결과 수신 보안 — 잘못된 콜백 시크릿이면 401")
    void ingest_withWrongCallbackSecret_returns401() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("done", "rule_v1", List.of());
        mockMvc.perform(post("/api/analysis/videos/" + videoId)
                        .header("X-AI-Callback-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("C002"));
    }

    @Test
    @DisplayName("분석 재시도 — 소유자가 재시도하면 status가 pending으로 돌아가고 결과가 비워진다")
    void retryAnalysis_byOwner() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);
        ingest(videoId, new AnalysisIngestRequest("done", "rule_v1", List.of(
                new AnalysisSegmentPayload(0, 0, 1000, "highstep", false, 0.9f))));

        mockMvc.perform(post("/api/videos/" + videoId + "/analysis/retry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.techniques").isEmpty())
                .andExpect(jsonPath("$.data.segments").doesNotExist());
    }

    @Test
    @DisplayName("분석 재시도 실패 — 소유자가 아니면 403")
    void retryAnalysis_byOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner);

        mockMvc.perform(post("/api/videos/" + videoId + "/analysis/retry")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("분석 피드백 — 소유자가 등록하면 final 결과만 갱신하고 AI 원본은 유지한다")
    void submitFeedback_byOwner_updatesFinalOnly() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        ingest(videoId, new AnalysisIngestRequest("done", "rule_v3+flow_rf_v2", List.of(
                new AnalysisSegmentPayload(0, 0, 1000, "high_step", false, 0.9f)),
                List.of("high_step"), false, 0.18f));

        String request = """
                {
                  "techniques": ["flagging", "lock_off"],
                  "isDynamic": true,
                  "note": "lock_off도 사용한 것 같아요"
                }
                """;
        mockMvc.perform(post("/api/videos/" + videoId + "/analysis/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.videoId").value(videoId));

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.techniques[0]").value("flagging"))
                .andExpect(jsonPath("$.data.techniques[1]").value("lock_off"))
                .andExpect(jsonPath("$.data.isDynamic").value(true))
                .andExpect(jsonPath("$.data.feedbackApplied").value(true));

        String aiTechniques = jdbcTemplate.queryForObject(
                "SELECT ai_techniques::text FROM analysis_video_results WHERE video_id = ?",
                String.class, videoId);
        String finalTechniques = jdbcTemplate.queryForObject(
                "SELECT final_techniques::text FROM analysis_video_results WHERE video_id = ?",
                String.class, videoId);
        Boolean aiIsDynamic = jdbcTemplate.queryForObject(
                "SELECT ai_is_dynamic FROM analysis_video_results WHERE video_id = ?",
                Boolean.class, videoId);
        Boolean finalIsDynamic = jdbcTemplate.queryForObject(
                "SELECT final_is_dynamic FROM analysis_video_results WHERE video_id = ?",
                Boolean.class, videoId);

        org.assertj.core.api.Assertions.assertThat(aiTechniques).isEqualTo("[\"high_step\"]");
        org.assertj.core.api.Assertions.assertThat(finalTechniques).isEqualTo("[\"flagging\", \"lock_off\"]");
        org.assertj.core.api.Assertions.assertThat(aiIsDynamic).isFalse();
        org.assertj.core.api.Assertions.assertThat(finalIsDynamic).isTrue();
    }

    @Test
    @DisplayName("분석 피드백 실패 — 공개 영상이어도 소유자가 아니면 등록할 수 없다")
    void submitFeedback_publicVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);

        String request = """
                {
                  "techniques": ["flagging"],
                  "isDynamic": false
                }
                """;
        mockMvc.perform(post("/api/videos/" + videoId + "/analysis/feedback")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    // ===== helpers =====

    private void ingest(long videoId, AnalysisIngestRequest request) throws Exception {
        mockMvc.perform(aiCallbackPost(videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder aiCallbackPost(long videoId) {
        return post("/api/analysis/videos/" + videoId)
                .header("X-AI-Callback-Secret", AI_CALLBACK_SECRET);
    }

    private long createVideo(String token) throws Exception {
        return createVideo(token, true);
    }

    private long createVideo(String token, boolean isPublic) throws Exception {
        long userId = dataOf(mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).path("userId").asLong();
        String path = "videos/uploads/" + userId + "/test-" + java.util.UUID.randomUUID() + ".mp4";
        var request = new CreateVideoRequest(1L, "My Send", "a clean ascent", 1003L,
                path, null, 45, java.time.LocalDate.of(2026, 6, 3), isPublic);
        return dataOf(mockMvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()))
                .path("id").asLong();
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
