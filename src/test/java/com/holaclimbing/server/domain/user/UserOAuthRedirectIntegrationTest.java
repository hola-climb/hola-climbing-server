package com.holaclimbing.server.domain.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.terms.dto.request.TermAgreementRequest;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.dto.request.LogoutRequest;
import com.holaclimbing.server.domain.user.dto.request.OAuthResultRequest;
import com.holaclimbing.server.domain.user.dto.request.OAuthSignupRequest;
import com.holaclimbing.server.domain.user.dto.request.RefreshRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.user.oauth.OAuthAuthorizationCodeRequest;
import com.holaclimbing.server.domain.user.oauth.OAuthProvider;
import com.holaclimbing.server.domain.user.oauth.OAuthProviderClient;
import com.holaclimbing.server.domain.user.oauth.OAuthProviderClientResolver;
import com.holaclimbing.server.domain.user.oauth.OAuthUserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "app.base-url=http://localhost:8080",
        "app.oauth.state-ttl-minutes=5",
        "app.oauth.result-ttl-minutes=1",
        "app.oauth.pending-signup-ttl-minutes=10",
        "app.oauth.allowed-redirect-uris=http://localhost:5173/oauth/callback",
        "app.oauth.providers.google.client-id=test-google-client",
        "app.oauth.providers.google.client-secret=test-google-secret",
        "app.oauth.providers.google.authorization-uri=https://accounts.google.com/o/oauth2/v2/auth",
        "app.oauth.providers.google.token-uri=https://oauth2.googleapis.com/token",
        "app.oauth.providers.google.user-info-uri=https://openidconnect.googleapis.com/v1/userinfo",
        "app.oauth.providers.google.scope=openid,email,profile",
        "app.oauth.providers.kakao.client-id=test-kakao-client",
        "app.oauth.providers.kakao.client-secret=test-kakao-secret",
        "app.oauth.providers.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
        "app.oauth.providers.kakao.token-uri=https://kauth.kakao.com/oauth/token",
        "app.oauth.providers.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
        "app.oauth.providers.kakao.scope=account_email,profile_nickname,profile_image",
        "app.oauth.providers.naver.client-id=test-naver-client",
        "app.oauth.providers.naver.client-secret=test-naver-secret",
        "app.oauth.providers.naver.authorization-uri=https://nid.naver.com/oauth2.0/authorize",
        "app.oauth.providers.naver.token-uri=https://nid.naver.com/oauth2.0/token",
        "app.oauth.providers.naver.user-info-uri=https://openapi.naver.com/v1/nid/me",
        "app.oauth.providers.naver.scope=email,nickname,profile_image",
        "app.oauth.providers.apple.client-id=test.apple.service",
        "app.oauth.providers.apple.client-secret=",
        "app.oauth.providers.apple.authorization-uri=https://appleid.apple.com/auth/authorize",
        "app.oauth.providers.apple.token-uri=https://appleid.apple.com/auth/token",
        "app.oauth.providers.apple.user-info-uri=",
        "app.oauth.providers.apple.scope=openid,email,name",
        "app.oauth.providers.apple.jwks-uri=https://appleid.apple.com/auth/keys",
        "app.oauth.providers.apple.response-mode=form_post",
        "app.oauth.providers.apple.team-id=TEAM1234567",
        "app.oauth.providers.apple.key-id=KEY1234567",
        "app.oauth.providers.apple.private-key-base64=dGVzdC1rZXk=",
        "app.oauth.providers.apple.client-secret-ttl-days=30"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, UserOAuthRedirectIntegrationTest.FakeOAuthProviderConfig.class})
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/terms-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserOAuthRedirectIntegrationTest {

    private static final String FRONTEND_CALLBACK = "http://localhost:5173/oauth/callback";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("OAuth authorize redirects to provider and stores state in Redis")
    void authorize_redirectsToProviderAndStoresState() throws Exception {
        String location = authorizeAndGetProviderLocation();
        Map<String, String> query = queryOf(location);

        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(query).containsEntry("response_type", "code");
        assertThat(query).containsEntry("client_id", "test-google-client");
        assertThat(query).containsEntry("redirect_uri", "http://localhost:8080/api/auth/oauth/google/callback");
        assertThat(query).containsEntry("scope", "openid email profile");
        assertThat(query.get("state")).isNotBlank();
        assertThat(redis.hasKey("auth:oauth:state:" + query.get("state"))).isTrue();
    }

    @Test
    @DisplayName("Apple OAuth authorize redirects to Apple with form_post response mode and nonce")
    void appleAuthorize_redirectsWithFormPostAndNonce() throws Exception {
        String location = authorizeAndGetProviderLocation("apple");
        Map<String, String> query = queryOf(location);

        assertThat(location).startsWith("https://appleid.apple.com/auth/authorize?");
        assertThat(query).containsEntry("response_type", "code");
        assertThat(query).containsEntry("client_id", "test.apple.service");
        assertThat(query).containsEntry("redirect_uri", "http://localhost:8080/api/auth/oauth/apple/callback");
        assertThat(query).containsEntry("scope", "openid email name");
        assertThat(query).containsEntry("response_mode", "form_post");
        assertThat(query.get("state")).isNotBlank();
        assertThat(query.get("nonce")).isNotBlank();
        assertThat(redis.hasKey("auth:oauth:state:" + query.get("state"))).isTrue();
    }

    @Test
    @DisplayName("OAuth callback for existing social user redirects with one-time oauthCode and returns Hola tokens")
    void callback_existingSocialUser_returnsTokenThroughOneTimeCode() throws Exception {
        User existing = User.builder()
                .email("existing-google@hola.com")
                .emailVerified(true)
                .provider("google")
                .providerId("existing-google-id")
                .nickname("existinggoogle")
                .profileImage("https://example.com/existing.png")
                .build();
        userMapper.insertOAuth(existing);

        String oauthCode = callbackAndExtractOauthCode("existing-code");

        JsonNode data = dataOf(mockMvc.perform(post("/api/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signupRequired").value(false))
                .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.token.refreshToken").isNotEmpty()));

        String accessToken = data.path("token").path("accessToken").asText();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("existinggoogle"));
    }

    @Test
    @DisplayName("OAuth callback for new social user returns pending signup and signup creates user")
    void callback_newSocialUser_signupCreatesUserAndReturnsToken() throws Exception {
        String oauthCode = callbackAndExtractOauthCode("new-code");

        JsonNode resultData = dataOf(mockMvc.perform(post("/api/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signupRequired").value(true))
                .andExpect(jsonPath("$.data.signupToken").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("new-google@hola.com"))
                .andExpect(jsonPath("$.data.suggestedNickname").value("New Google")));

        String signupToken = resultData.path("signupToken").asText();

        JsonNode tokenData = dataOf(mockMvc.perform(post("/api/auth/oauth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthSignupRequest(
                                signupToken,
                                "googlejoiner",
                                List.of(
                                        new TermAgreementRequest(1L, true),
                                        new TermAgreementRequest(2L, true),
                                        new TermAgreementRequest(3L, false),
                                        new TermAgreementRequest(4L, true)
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty()));

        User saved = userMapper.findByProvider("google", "new-google-id");
        assertThat(saved).isNotNull();
        assertThat(saved.getNickname()).isEqualTo("googlejoiner");
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getProvider()).isEqualTo("google");
        assertThat(saved.isEmailVerified()).isTrue();

        String accessToken = tokenData.path("accessToken").asText();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("googlejoiner"));
    }

    @Test
    @DisplayName("OAuth callback for existing local email returns email-existing status without signup token")
    void callback_existingLocalEmail_returnsEmailAlreadyExistsStatus() throws Exception {
        User localUser = User.builder()
                .email("local-email@hola.com")
                .passwordHash("hashed-password")
                .emailVerified(true)
                .nickname("localemail")
                .build();
        userMapper.insert(localUser);

        String oauthCode = callbackAndExtractOauthCode("local-email-code");

        JsonNode resultData = dataOf(mockMvc.perform(post("/api/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.data.signupRequired").value(false))
                .andExpect(jsonPath("$.data.email").value("local-email@hola.com")));

        assertThat(resultData.hasNonNull("token")).isFalse();
        assertThat(resultData.hasNonNull("signupToken")).isFalse();
        assertThat(userMapper.findByProvider("google", "local-email-google-id")).isNull();
    }

    @Test
    @DisplayName("OAuth one-time result code cannot be reused")
    void resultCode_isOneTimeOnly() throws Exception {
        User existing = User.builder()
                .email("existing-google@hola.com")
                .emailVerified(true)
                .provider("google")
                .providerId("existing-google-id")
                .nickname("existinggoogle")
                .build();
        userMapper.insertOAuth(existing);

        String oauthCode = callbackAndExtractOauthCode("existing-code");

        mockMvc.perform(post("/api/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("U019"));
    }

    @Test
    @DisplayName("OAuth-issued refresh token uses existing refresh rotation policy")
    void oauthIssuedRefreshToken_usesRotationPolicy() throws Exception {
        User existing = User.builder()
                .email("existing-google@hola.com")
                .emailVerified(true)
                .provider("google")
                .providerId("existing-google-id")
                .nickname("existinggoogle")
                .build();
        userMapper.insertOAuth(existing);

        String oauthCode = callbackAndExtractOauthCode("existing-code");
        JsonNode token = dataOf(mockMvc.perform(post("/api/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
                .andExpect(status().isOk())).path("token");

        String oldRefresh = token.path("refreshToken").asText();
        String newRefresh = dataOf(mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(oldRefresh))))
                .andExpect(status().isOk()))
                .path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(oldRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U005"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(newRefresh))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OAuth-issued access token uses existing logout blacklist policy")
    void oauthIssuedAccessToken_logoutBlacklistsToken() throws Exception {
        User existing = User.builder()
                .email("existing-google@hola.com")
                .emailVerified(true)
                .provider("google")
                .providerId("existing-google-id")
                .nickname("existinggoogle")
                .build();
        userMapper.insertOAuth(existing);

        String oauthCode = callbackAndExtractOauthCode("existing-code");
        JsonNode token = dataOf(mockMvc.perform(post("/api/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
                .andExpect(status().isOk())).path("token");

        String accessToken = token.path("accessToken").asText();
        String refreshToken = token.path("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OAuth callback rejects unknown state")
    void callback_unknownState_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/google/callback")
                        .param("code", "existing-code")
                        .param("state", "missing-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("U018"));
    }

    private String authorizeAndGetProviderLocation() throws Exception {
        return authorizeAndGetProviderLocation("google");
    }

    private String authorizeAndGetProviderLocation(String provider) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/{provider}/authorize", provider)
                        .param("redirectUri", FRONTEND_CALLBACK))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);
    }

    private String callbackAndExtractOauthCode(String providerCode) throws Exception {
        String authorizeLocation = authorizeAndGetProviderLocation();
        String state = queryOf(authorizeLocation).get("state");

        String frontendLocation = mockMvc.perform(get("/api/auth/oauth/google/callback")
                        .param("code", providerCode)
                        .param("state", state))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);

        assertThat(frontendLocation).startsWith(FRONTEND_CALLBACK + "?");
        String oauthCode = queryOf(frontendLocation).get("oauthCode");
        assertThat(oauthCode).isNotBlank();
        return oauthCode;
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private static Map<String, String> queryOf(String location) {
        String query = URI.create(location).getRawQuery();
        assertThat(query).isNotBlank();
        return List.of(query.split("&")).stream()
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]),
                        pair -> pair.length > 1 ? decode(pair[1]) : ""
                ));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @TestConfiguration
    static class FakeOAuthProviderConfig {

        @Bean
        @Primary
        OAuthProviderClientResolver fakeOAuthProviderClientResolver() {
            return new OAuthProviderClientResolver(List.of(new OAuthProviderClient() {
                @Override
                public OAuthProvider provider() {
                    return OAuthProvider.GOOGLE;
                }

                @Override
                public OAuthUserProfile fetchProfile(OAuthAuthorizationCodeRequest request) {
                    return switch (request.code()) {
                        case "existing-code" -> new OAuthUserProfile(
                                OAuthProvider.GOOGLE,
                                "existing-google-id",
                                "existing-google@hola.com",
                                "Existing Google",
                                "https://example.com/existing.png"
                        );
                        case "new-code" -> new OAuthUserProfile(
                                OAuthProvider.GOOGLE,
                                "new-google-id",
                                "new-google@hola.com",
                                "New Google",
                                "https://example.com/new.png"
                        );
                        case "local-email-code" -> new OAuthUserProfile(
                                OAuthProvider.GOOGLE,
                                "local-email-google-id",
                                "local-email@hola.com",
                                "Local Email Google",
                                "https://example.com/local.png"
                        );
                        default -> throw new AssertionError("Unexpected OAuth code: " + request.code());
                    };
                }
            }));
        }
    }
}
