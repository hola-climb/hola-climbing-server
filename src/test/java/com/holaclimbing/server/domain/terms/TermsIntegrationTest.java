package com.holaclimbing.server.domain.terms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.terms.dto.request.AgreeTermsRequest;
import com.holaclimbing.server.domain.terms.dto.request.TermAgreementRequest;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Terms 도메인 통합 테스트 — 활성 약관 조회, 동의 기록, 회원가입 약관 검증.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/terms-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TermsIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final long TERM_SERVICE = 1L;
    private static final long TERM_PRIVACY = 2L;
    private static final long TERM_MARKETING = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("활성 약관 조회 — 발효 중인 약관 3종을 반환한다")
    void getActiveTerms_returnsActiveTerms() throws Exception {
        // findActiveTerms는 type 오름차순 정렬 → marketing, privacy, service
        mockMvc.perform(get("/api/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].type").value("marketing"))
                .andExpect(jsonPath("$.data[0].required").value(false))
                .andExpect(jsonPath("$.data[2].type").value("service"))
                .andExpect(jsonPath("$.data[2].required").value(true));
    }

    @Test
    @DisplayName("회원가입 — 필수 약관에 모두 동의하면 가입된다")
    void signup_withRequiredTerms_success() throws Exception {
        var request = new SignupRequest("a@hola.com", PASSWORD, "climberone", List.of(
                new TermAgreementRequest(TERM_SERVICE, true),
                new TermAgreementRequest(TERM_PRIVACY, true),
                new TermAgreementRequest(TERM_MARKETING, false)));
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("회원가입 실패 — 필수 약관 미동의는 400 U010")
    void signup_missingRequiredTerm_returns400() throws Exception {
        var request = new SignupRequest("a@hola.com", PASSWORD, "climberone", List.of(
                new TermAgreementRequest(TERM_SERVICE, true)));  // privacy 누락
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U010"));
    }

    @Test
    @DisplayName("약관 동의 기록 — 인증 사용자는 동의를 기록할 수 있다")
    void agree_success() throws Exception {
        String token = register("a@hola.com", "climberone");

        var request = new AgreeTermsRequest(List.of(new TermAgreementRequest(TERM_MARKETING, true)));
        mockMvc.perform(post("/api/terms/agree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("약관 동의 실패 — 토큰 없이 호출하면 401")
    void agree_withoutToken_returns401() throws Exception {
        var request = new AgreeTermsRequest(List.of(new TermAgreementRequest(TERM_MARKETING, true)));
        mockMvc.perform(post("/api/terms/agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("약관 동의 실패 — 유효하지 않은 termId는 400")
    void agree_invalidTermId_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        var request = new AgreeTermsRequest(List.of(new TermAgreementRequest(999L, true)));
        mockMvc.perform(post("/api/terms/agree")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ===== helpers =====

    /** 필수 약관에 동의하며 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken 반환. */
    private String register(String email, String nickname) throws Exception {
        var request = new SignupRequest(email, PASSWORD, nickname, List.of(
                new TermAgreementRequest(TERM_SERVICE, true),
                new TermAgreementRequest(TERM_PRIVACY, true)));
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
                .path("access_token").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
