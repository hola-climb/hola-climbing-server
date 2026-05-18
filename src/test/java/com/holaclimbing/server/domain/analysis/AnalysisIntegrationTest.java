package com.holaclimbing.server.domain.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisSegmentPayload;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
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

        mockMvc.perform(get("/api/analysis/videos/" + videoId))
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
                .andExpect(jsonPath("$.data.model_version").value("rule_v1"))
                .andExpect(jsonPath("$.data.segments.length()").value(2))
                .andExpect(jsonPath("$.data.segments[0].technique").value("highstep"))
                .andExpect(jsonPath("$.data.segments[1].is_dynamic").value(true));

        mockMvc.perform(get("/api/analysis/videos/" + videoId))
                .andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.segments.length()").value(2));
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

        mockMvc.perform(get("/api/analysis/videos/" + videoId))
                .andExpect(jsonPath("$.data.segments.length()").value(1))
                .andExpect(jsonPath("$.data.model_version").value("lstm_v1"))
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
        mockMvc.perform(get("/api/analysis/videos/999999"))
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

    // ===== helpers =====

    private void ingest(long videoId, AnalysisIngestRequest request) throws Exception {
        mockMvc.perform(post("/api/analysis/videos/" + videoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private long createVideo(String token) throws Exception {
        var request = new CreateVideoRequest(null, "My Send", "a clean ascent", "V5",
                "gs://hola-bucket/video.mp4", null, 45, true);
        return dataOf(mockMvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(get("/api/users/verify-email").param("token", user.getEmailVerificationToken()))
                .andExpect(status().isOk());
        return dataOf(mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD)))))
                .path("access_token").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
