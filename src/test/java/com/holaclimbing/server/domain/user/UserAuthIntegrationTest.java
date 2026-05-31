package com.holaclimbing.server.domain.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.LogoutRequest;
import com.holaclimbing.server.domain.user.dto.request.PasswordResetEmailRequest;
import com.holaclimbing.server.domain.user.dto.request.PasswordResetRequest;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("회원가입 성공 — 201, 미인증 상태로 저장되고 인증 토큰이 발급된다")
    void signup_success() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

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
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("tokenType").asText()).isEqualTo("Bearer");
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
        String refreshToken = dataOf(login(EMAIL, PASSWORD)).path("refreshToken").asText();

        var data = dataOf(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk()));
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("토큰 재발급 회전 — 사용한 refresh 토큰을 다시 쓰면 401 (재사용 탐지)")
    void refresh_rotation_oldTokenRejected() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);
        String oldRefresh = dataOf(login(EMAIL, PASSWORD)).path("refreshToken").asText();

        // 1차 회전 — 새 토큰 발급 성공, oldRefresh는 폐기됨.
        String newRefresh = dataOf(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(oldRefresh))))
                .andExpect(status().isOk())).path("refreshToken").asText();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(oldRefresh);

        // 폐기된 oldRefresh 재사용 시도 → 401 U005
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(oldRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U005"));

        // 새 refresh 토큰은 정상 동작
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(newRefresh))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 재발급 실패 — Access 토큰을 보내면 401 U005")
    void refresh_withAccessToken_returns401() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);
        String accessToken = dataOf(login(EMAIL, PASSWORD)).path("accessToken").asText();

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

    @Test
    @DisplayName("로그아웃 — 블랙리스트 등록 후 같은 Access 토큰은 401로 거부된다")
    void logout_blacklistsAccessToken() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);
        var tokens = dataOf(login(EMAIL, PASSWORD).andExpect(status().isOk()));
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isOk());

        // 로그아웃된 토큰으로 보호 API 접근 시 필터가 401로 거부
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호 재설정 — 재설정 후 새 비밀번호로 로그인된다")
    void passwordReset_flow() throws Exception {
        signup(EMAIL, PASSWORD, NICKNAME).andExpect(status().isCreated());
        verifyEmailOf(EMAIL);

        mockMvc.perform(post("/api/auth/password/reset-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetEmailRequest(EMAIL))))
                .andExpect(status().isOk());

        String resetToken = redis.keys("auth:pwreset:*").stream().findFirst()
                .map(k -> k.substring("auth:pwreset:".length()))
                .orElseThrow(() -> new AssertionError("재설정 토큰이 저장되지 않았습니다"));

        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetRequest(resetToken, "newpassword456"))))
                .andExpect(status().isOk());

        login(EMAIL, "newpassword456").andExpect(status().isOk());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀번호 재설정 실패 — 유효하지 않은 토큰은 400 U011")
    void passwordReset_invalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordResetRequest("bogus-token-xyz", "newpassword456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U011"));
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
