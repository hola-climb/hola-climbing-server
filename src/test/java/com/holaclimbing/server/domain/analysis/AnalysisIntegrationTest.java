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
class AnalysisIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("분석 조회 — 분석 전 영상은 status=pending, segments 비어 있음")
    void getAnalysis_beforeAnalysis() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.segments").isEmpty());
    }

    @Test
    @DisplayName("결과 수신 — done 결과를 받으면 세그먼트를 저장하고 영상 status=done")
    void ingest_done_storesSegments() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("done", "rule_v1", List.of(
                new AnalysisSegmentPayload(0, 0, 1000, "highstep", false, 0.91f),
                new AnalysisSegmentPayload(1, 1000, 2000, "dyno", true, 0.85f)));

        mockMvc.perform(post("/api/analysis/videos/" + videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.modelVersion").value("rule_v1"))
                .andExpect(jsonPath("$.data.segments.length()").value(2))
                .andExpect(jsonPath("$.data.segments[0].technique").value("highstep"))
                .andExpect(jsonPath("$.data.segments[1].isDynamic").value(true));

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis"))
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.segments.length()").value(2));
    }

    @Test
    @DisplayName("결과 수신 — AI 워커 snake_case JSON을 그대로 수신해 저장한다")
    void ingest_workerSnakeCasePayload_storesSegments() throws Exception {
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
                  ]
                }
                """;

        mockMvc.perform(post("/api/analysis/videos/" + videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workerPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.modelVersion").value("rule_v1"))
                .andExpect(jsonPath("$.data.segments.length()").value(1))
                .andExpect(jsonPath("$.data.segments[0].sequenceIndex").value(0))
                .andExpect(jsonPath("$.data.segments[0].startTimeMs").value(0))
                .andExpect(jsonPath("$.data.segments[0].endTimeMs").value(1000))
                .andExpect(jsonPath("$.data.segments[0].isDynamic").value(false));
    }

    @Test
    @DisplayName("결과 수신 — failed 결과를 받으면 영상 status=failed")
    void ingest_failed_setsStatusFailed() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("failed", null, null);
        mockMvc.perform(post("/api/analysis/videos/" + videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.data.segments").isEmpty());
    }

    @Test
    @DisplayName("결과 수신 — 재분석 시 기존 결과를 새 결과로 대체한다")
    void ingest_reanalysis_replacesPrevious() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        ingest(videoId, new AnalysisIngestRequest("done", "rule_v1", List.of(
                new AnalysisSegmentPayload(0, 0, 1000, "highstep", false, 0.9f),
                new AnalysisSegmentPayload(1, 1000, 2000, "flagging", false, 0.8f),
                new AnalysisSegmentPayload(2, 2000, 3000, "dyno", true, 0.7f))));
        ingest(videoId, new AnalysisIngestRequest("done", "lstm_v1", List.of(
                new AnalysisSegmentPayload(0, 0, 1500, "lock_off", false, 0.95f))));

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis"))
                .andExpect(jsonPath("$.data.segments.length()").value(1))
                .andExpect(jsonPath("$.data.modelVersion").value("lstm_v1"))
                .andExpect(jsonPath("$.data.segments[0].technique").value("lock_off"));
    }

    @Test
    @DisplayName("결과 수신 — 잘못된 status는 400")
    void ingest_invalidStatus_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisIngestRequest("weird", "rule_v1", List.of());
        mockMvc.perform(post("/api/analysis/videos/" + videoId)
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
        mockMvc.perform(post("/api/analysis/videos/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
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
                .andExpect(jsonPath("$.data.segments").isEmpty());
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
    @DisplayName("분석 피드백 — 등록하면 201과 labelId를 반환한다")
    void submitFeedback_success() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token);

        var request = new AnalysisFeedbackRequest("highstep", 12.5, false, "flagging", "이건 플래깅이에요");
        mockMvc.perform(post("/api/videos/" + videoId + "/analysis/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.labelId").isNumber());
    }

    // ===== helpers =====

    private void ingest(long videoId, AnalysisIngestRequest request) throws Exception {
        mockMvc.perform(post("/api/analysis/videos/" + videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private long createVideo(String token) throws Exception {
        long userId = dataOf(mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).path("userId").asLong();
        String path = "videos/uploads/" + userId + "/test-" + java.util.UUID.randomUUID() + ".mp4";
        var request = new CreateVideoRequest(1L, "My Send", "a clean ascent", 1003L,
                path, null, 45, java.time.LocalDate.of(2026, 6, 3), true);
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
