package com.holaclimbing.server.domain.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import static com.holaclimbing.server.TestSignupRequests.signupRequest;
import com.holaclimbing.server.domain.report.dto.request.CreateReportRequest;
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
 * Report 도메인(신고 등록) 통합 테스트.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/terms-data.sql",
        "classpath:sql/reports-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReportIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("신고 등록 성공 — 201, reportId 반환")
    void createReport_success() throws Exception {
        String token = register("a@hola.com", "climberone");
        long targetId = userId("victim@hola.com", "targetuser");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("user", targetId, "spam", "스팸 계정입니다"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportId").isNumber());
    }

    @Test
    @DisplayName("신고 등록 — reason 없이도 등록된다")
    void createReport_withoutReason_success() throws Exception {
        String token = register("a@hola.com", "climberone");
        long targetId = userId("victim@hola.com", "targetuser");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("user", targetId, "abuse", null))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("신고 등록 — 사용자와 연관없음 category도 등록된다")
    void createReport_irrelevantCategory_success() throws Exception {
        String token = register("a@hola.com", "climberone");
        long targetId = userId("victim@hola.com", "targetuser");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("user", targetId, "irrelevant", "사용자와 연관없음"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportId").isNumber());
    }

    @Test
    @DisplayName("신고 등록 실패 — 토큰 없이 호출하면 401")
    void createReport_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("user", 1L, "spam", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("신고 등록 실패 — 자기 자신 신고는 400 R001")
    void createReport_self_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");
        long myId = userMapper.findByEmail("a@hola.com").getId();

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("user", myId, "spam", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("R001"));
    }

    @Test
    @DisplayName("신고 등록 실패 — 같은 대상 중복 신고는 409 R002")
    void createReport_duplicate_returns409() throws Exception {
        String token = register("a@hola.com", "climberone");
        long targetId = userId("victim@hola.com", "targetuser");
        var body = objectMapper.writeValueAsString(
                new CreateReportRequest("user", targetId, "spam", null));

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("R002"));
    }

    @Test
    @DisplayName("신고 등록 실패 — 유효하지 않은 category는 400")
    void createReport_invalidCategory_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");
        long targetId = userId("victim@hola.com", "targetuser");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("user", targetId, "boring", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("신고 등록 실패 — 유효하지 않은 targetType은 400")
    void createReport_invalidTargetType_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("playlist", 1L, "spam", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("신고 등록 실패 — 없는 사용자 신고는 404 U001")
    void createReport_targetUserNotFound_returns404() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateReportRequest("user", 999999L, "spam", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    // ===== helpers =====

    private long userId(String email, String nickname) throws Exception {
        register(email, nickname);
        return userMapper.findByEmail(email).getId();
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
