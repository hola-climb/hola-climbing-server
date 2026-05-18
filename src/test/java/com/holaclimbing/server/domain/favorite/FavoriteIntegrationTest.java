package com.holaclimbing.server.domain.favorite;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Favorite 도메인(암장 즐겨찾기) 통합 테스트.
 * JWT로 인증한 사용자가 암장을 즐겨찾기에 추가/제거/조회하는 전 구간을 검증한다.
 * gym 5는 status='closed'라 즐겨찾기 대상이 될 수 없다.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/favorites-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FavoriteIntegrationTest {

    private static final String EMAIL = "fan@hola.com";
    private static final String PASSWORD = "password123";
    private static final String NICKNAME = "gymfan";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("즐겨찾기 추가 실패 — 토큰 없이 호출하면 401")
    void addFavorite_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/favorites/gyms/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("즐겨찾기 추가 성공 — 추가한 암장이 목록에 나타난다")
    void addFavorite_thenListed() throws Exception {
        String token = register();

        mockMvc.perform(post("/api/favorites/gyms/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/favorites/gyms/3").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/favorites/gyms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("즐겨찾기 추가 실패 — 이미 즐겨찾기한 암장은 400")
    void addFavorite_duplicate_returns400() throws Exception {
        String token = register();

        mockMvc.perform(post("/api/favorites/gyms/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/favorites/gyms/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("즐겨찾기 추가 실패 — 없는 암장은 404 G001")
    void addFavorite_nonexistentGym_returns404() throws Exception {
        String token = register();

        mockMvc.perform(post("/api/favorites/gyms/999999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("즐겨찾기 추가 실패 — closed 상태 암장은 404 G001")
    void addFavorite_closedGym_returns404() throws Exception {
        String token = register();

        mockMvc.perform(post("/api/favorites/gyms/5").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("즐겨찾기 제거 — 제거 후 목록에서 사라진다")
    void removeFavorite_success() throws Exception {
        String token = register();

        mockMvc.perform(post("/api/favorites/gyms/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/favorites/gyms/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/favorites/gyms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(0));
    }

    @Test
    @DisplayName("즐겨찾기 목록 — 추가한 적 없으면 빈 목록")
    void getFavoriteGyms_empty() throws Exception {
        String token = register();

        mockMvc.perform(get("/api/favorites/gyms").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    // ===== helpers =====

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
    private String register() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(EMAIL, PASSWORD, NICKNAME))))
                .andExpect(status().isCreated());

        var user = userMapper.findByEmail(EMAIL);
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());

        return dataOf(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD)))))
                .path("access_token").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
