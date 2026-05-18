package com.holaclimbing.server.domain.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.RefreshRequest;
import com.holaclimbing.server.domain.user.dto.request.ResendVerificationRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User 도메인 인증 플로우 통합 테스트.
 * Testcontainers PostgreSQL 위에서 회원가입→이메일 인증→로그인→토큰 재발급 전 구간을 검증한다.
 * 각 테스트 메서드 실행 전 users 테이블을 재생성하여 독립성을 보장한다.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = "classpath:sql/users-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserAuthIntegrationTest {

    private static final String EMAIL = "climber@hola.com";
    private static final String PASSWORD = "password123";
    private static final String NICKNAME = "boulderking";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("회원가입 성공 — 201, 미인증 상태로 저장되고 인증 토큰이 발급된다")
    void signup_success() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.email_verified").value(false));

        var saved = userMapper.findByEmail(EMAIL);
        assertThat(saved).isNotNull();
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getEmailVerificationToken()).isNotBlank();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD);
    }

    @Test
    @DisplayName("회원가입 실패 — 이메일 중복 시 409 U002")
    void signup_duplicateEmail_returns409() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());

        signup(EMAIL, PASSWORD, "anothernick")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U002"));
    }

    @Test
    @DisplayName("회원가입 실패 — 닉네임 중복 시 409 U008")
    void signup_duplicateNickname_returns409() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());

        signup("other@hola.com", PASSWORD, NICKNAME)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U008"));
    }

    @Test
    @DisplayName("회원가입 실패 — 잘못된 이메일 형식은 400 C001")
    void signup_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password123\",\"nickname\":\"boulderking\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("회원가입 실패 — 8자 미만 비밀번호는 400 C001")
    void signup_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"climber@hola.com\",\"password\":\"short\",\"nickname\":\"boulderking\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("로그인 실패 — 이메일 미인증 상태면 403 U004")
    void login_beforeEmailVerification_returns403() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("U004"));
    }

    @Test
    @DisplayName("골든 패스 — 회원가입→이메일 인증→로그인 시 토큰이 발급된다")
    void verifyEmailThenLogin_success() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);

        var data = dataOf(login(EMAIL, PASSWORD).andExpect(status().isOk()));
        assertThat(data.path("access_token").asText()).isNotBlank();
        assertThat(data.path("refresh_token").asText()).isNotBlank();
        assertThat(data.path("token_type").asText()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("이메일 인증 실패 — 존재하지 않는 토큰은 401 U005")
    void verifyEmail_invalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest("bogus-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U005"));
    }

    @Test
    @DisplayName("로그인 실패 — 비밀번호 불일치는 401 U003")
    void login_wrongPassword_returns401() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);

        login(EMAIL, "wrongpassword")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U003"));
    }

    @Test
    @DisplayName("로그인 실패 — 가입되지 않은 이메일은 404 U001")
    void login_unknownEmail_returns404() throws Exception {
        login("ghost@hola.com", PASSWORD)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("토큰 재발급 성공 — Refresh 토큰으로 새 토큰을 발급받는다")
    void refresh_success() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);
        String refreshToken = dataOf(login(EMAIL, PASSWORD)).path("refresh_token").asText();

        var data = dataOf(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk()));
        assertThat(data.path("access_token").asText()).isNotBlank();
        assertThat(data.path("refresh_token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("토큰 재발급 실패 — Access 토큰을 보내면 401 U005")
    void refresh_withAccessToken_returns401() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);
        String accessToken = dataOf(login(EMAIL, PASSWORD)).path("access_token").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(accessToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U005"));
    }

    @Test
    @DisplayName("중복 확인 — 가입 전후로 이메일/닉네임 사용 가능 여부가 바뀐다")
    void availabilityCheck_reflectsSignup() throws Exception {
        mockMvc.perform(get("/api/auth/email-check").param("email", EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/email-check").param("email", EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get("/api/auth/nickname-check").param("nickname", NICKNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("인증 메일 재발송 — 인증 토큰이 새 값으로 교체된다")
    void resendVerification_rotatesToken() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        String firstToken = userMapper.findByEmail(EMAIL).getEmailVerificationToken();

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResendVerificationRequest(EMAIL))))
                .andExpect(status().isOk());

        String secondToken = userMapper.findByEmail(EMAIL).getEmailVerificationToken();
        assertThat(secondToken).isNotBlank().isNotEqualTo(firstToken);
    }

    // ===== helpers =====

    private ResultActions signup(String email, String password, String nickname) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest(email, password, nickname))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, password))));
    }

    private void verifyEmailOf(String email) throws Exception {
        String token = userMapper.findByEmail(email).getEmailVerificationToken();
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest(token))))
                .andExpect(status().isOk());
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
