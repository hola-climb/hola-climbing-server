package com.holaclimbing.server.domain.stats;

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

/**
 * Stats 도메인(클라이밍 통계 조회) 통합 테스트.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/stats-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StatsIntegrationTest {

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
    @DisplayName("내 통계 — 분석 데이터가 있으면 동작별 횟수까지 반환한다")
    void getMyStats_withData() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedStats(userId, 5, 1200L, "{\"highstep\":12,\"flagging\":8}");

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(userId))
                .andExpect(jsonPath("$.data.total_videos").value(5))
                .andExpect(jsonPath("$.data.total_climbing_seconds").value(1200))
                .andExpect(jsonPath("$.data.technique_counts.highstep").value(12))
                .andExpect(jsonPath("$.data.technique_counts.flagging").value(8));
    }

    @Test
    @DisplayName("내 통계 — 분석 데이터가 없으면 0으로 채운 통계를 반환한다")
    void getMyStats_noData_returnsZeros() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(get("/api/stats/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_videos").value(0))
                .andExpect(jsonPath("$.data.total_climbing_seconds").value(0))
                .andExpect(jsonPath("$.data.technique_counts").isEmpty());
    }

    @Test
    @DisplayName("내 통계 — 토큰 없이 호출하면 401")
    void getMyStats_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/stats/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("사용자 통계 — userId로 특정 사용자 통계를 조회한다")
    void getUserStats_byId_success() throws Exception {
        register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedStats(userId, 3, 600L, "{\"dyno\":4}");

        mockMvc.perform(get("/api/stats/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_videos").value(3))
                .andExpect(jsonPath("$.data.technique_counts.dyno").value(4));
    }

    @Test
    @DisplayName("사용자 통계 — 없는 사용자는 404 U001")
    void getUserStats_nonexistentUser_returns404() throws Exception {
        mockMvc.perform(get("/api/stats/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("기술별 통계 — 최다/최소 사용 기술을 함께 반환한다")
    void getTechniqueStats_withData() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        seedStats(userId, 5, 1200L, "{\"highstep\":12,\"flagging\":8,\"dyno\":3}");

        mockMvc.perform(get("/api/stats/me/techniques").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.technique_counts.highstep").value(12))
                .andExpect(jsonPath("$.data.most_used").value("highstep"))
                .andExpect(jsonPath("$.data.least_used").value("dyno"));
    }

    @Test
    @DisplayName("기술별 통계 — 토큰 없이 호출하면 401")
    void getTechniqueStats_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/stats/me/techniques"))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private void seedStats(long userId, int totalVideos, long totalSeconds, String techniqueCountsJson) {
        jdbcTemplate.update(
                "INSERT INTO user_stats (user_id, total_videos, total_climbing_seconds, technique_counts) "
                        + "VALUES (?, ?, ?, ?::jsonb)",
                userId, totalVideos, totalSeconds, techniqueCountsJson);
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
                .path("access_token").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
