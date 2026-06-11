package com.holaclimbing.server.domain.stats;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.stats.dto.request.CreateClimbingLogRequest;
import com.holaclimbing.server.domain.stats.dto.request.UpdateClimbingLogRequest;
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
import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ClimbingLog 도메인 통합 테스트 — 클라이밍 기록 CRUD + 달력 조회 (F-03-03).
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/climbing-logs-schema.sql",
        "classpath:sql/videos-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ClimbingLogIntegrationTest {

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
    @DisplayName("기록 작성 — 201, 난이도별 개수와 총합이 저장된다")
    void createLog_success() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/climbing-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateClimbingLogRequest(
                                1L, LocalDate.of(2026, 5, 10), Map.of("빨강", 3, "파랑", 2), "좋은 날"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.gymId").value(1))
                .andExpect(jsonPath("$.data.totalProblems").value(5))
                .andExpect(jsonPath("$.data.gradeCounts.빨강").value(3));
    }

    @Test
    @DisplayName("기록 작성 실패 — 토큰 없이 호출하면 401")
    void createLog_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/climbing-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateClimbingLogRequest(
                                1L, LocalDate.of(2026, 5, 10), Map.of("빨강", 1), null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("기록 작성 실패 — 없는 암장은 404 G001")
    void createLog_nonexistentGym_returns404() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/climbing-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateClimbingLogRequest(
                                999999L, LocalDate.of(2026, 5, 10), Map.of("빨강", 1), null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("기록 작성 실패 — 난이도별 개수는 음수일 수 없다")
    void createLog_negativeGradeCount_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/climbing-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateClimbingLogRequest(
                                1L, LocalDate.of(2026, 5, 10), Map.of("빨강", -1), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("달력 실패 — 월 파라미터는 1~12 범위여야 한다")
    void calendar_invalidMonth_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(get("/api/stats/me/calendar")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026")
                        .param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("기록 수정·삭제 — 작성자만 가능하며 타인은 403")
    void updateAndDeleteLog_accessControl() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long logId = dataOf(mockMvc.perform(post("/api/climbing-logs")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateClimbingLogRequest(
                                1L, LocalDate.of(2026, 5, 10), Map.of("빨강", 2), "메모"))))
                .andExpect(status().isCreated()))
                .path("id").asLong();

        var update = objectMapper.writeValueAsString(new UpdateClimbingLogRequest(
                1L, LocalDate.of(2026, 5, 11), Map.of("초록", 4), "수정됨"));
        mockMvc.perform(patch("/api/climbing-logs/" + logId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/climbing-logs/" + logId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalProblems").value(4));

        mockMvc.perform(delete("/api/climbing-logs/" + logId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/climbing-logs/" + logId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("T001"));
    }

    @Test
    @DisplayName("달력 — 일별 기록·영상 수와 월간 합계를 반환한다")
    void calendar_monthlyAndByDate() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        createLog(token, LocalDate.of(2026, 5, 10), Map.of("빨강", 3));
        createLog(token, LocalDate.of(2026, 5, 10), Map.of("파랑", 2));
        createLog(token, LocalDate.of(2026, 5, 20), Map.of("초록", 1));
        createVideo(userId, 1L, 1002L, LocalDate.of(2026, 5, 10), 30);
        createVideo(userId, 1L, 1003L, LocalDate.of(2026, 5, 10), 40);
        createVideo(userId, 2L, 1004L, LocalDate.of(2026, 5, 11), 50);

        mockMvc.perform(get("/api/stats/me/calendar")
                        .header("Authorization", "Bearer " + token)
                        .param("year", "2026").param("month", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(5))
                .andExpect(jsonPath("$.data.totalVideos").value(3))
                .andExpect(jsonPath("$.data.totalProblems").value(6))
                .andExpect(jsonPath("$.data.totalVideoDurationSeconds").value(120))
                .andExpect(jsonPath("$.data.totalGymVisits").value(3))
                .andExpect(jsonPath("$.data.days.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-05-10"))
                .andExpect(jsonPath("$.data.days[0].logCount").value(2))
                .andExpect(jsonPath("$.data.days[0].totalProblems").value(5))
                .andExpect(jsonPath("$.data.days[0].videoCount").value(2))
                .andExpect(jsonPath("$.data.days[1].date").value("2026-05-11"))
                .andExpect(jsonPath("$.data.days[1].logCount").value(0))
                .andExpect(jsonPath("$.data.days[1].totalProblems").value(0))
                .andExpect(jsonPath("$.data.days[1].videoCount").value(1))
                .andExpect(jsonPath("$.data.days[2].date").value("2026-05-20"))
                .andExpect(jsonPath("$.data.days[2].logCount").value(1))
                .andExpect(jsonPath("$.data.days[2].totalProblems").value(1))
                .andExpect(jsonPath("$.data.days[2].videoCount").value(0));

        mockMvc.perform(get("/api/stats/me/calendar/2026-05-10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ===== helpers =====

    private void createLog(String token, LocalDate date, Map<String, Integer> counts) throws Exception {
        mockMvc.perform(post("/api/climbing-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateClimbingLogRequest(1L, date, counts, null))))
                .andExpect(status().isCreated());
    }

    private void createVideo(long userId, long gymId, long gymGradeId,
                             LocalDate recordedDate, int durationSeconds) {
        jdbcTemplate.update("""
                INSERT INTO videos (
                    user_id, gym_id, gym_grade_id, title, gcs_path,
                    duration_seconds, recorded_date, mime_type, status, is_public
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'video/mp4', 'completed', TRUE)
                """,
                userId, gymId, gymGradeId, "calendar clip",
                "videos/uploads/" + userId + "/calendar-" + recordedDate + "-" + durationSeconds + ".mp4",
                durationSeconds, recordedDate);
    }

    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
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
                .path("accessToken").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
