package com.holaclimbing.server.domain.admin;

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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/reports-schema.sql",
        "classpath:sql/videos-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminDashboardIntegrationTest {

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
    @DisplayName("관리자 홈 - 처리 대기 카운터를 반환한다")
    void getDashboard_returnsWorkQueueCounts() throws Exception {
        String adminToken = registerAndLoginAdmin();
        insertDashboardRows();

        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingGymCount").value(1))
                .andExpect(jsonPath("$.data.pendingReportCount").value(1))
                .andExpect(jsonPath("$.data.failedAnalysisVideoCount").value(1))
                .andExpect(jsonPath("$.data.newUserCountToday").value(1));
    }

    private void insertDashboardRows() {
        jdbcTemplate.update("""
                INSERT INTO gyms (id, name, address, region_code, status, created_by)
                VALUES (100, 'Pending Gym', 'Seoul', 'seoul', 'pending', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO gyms (id, name, address, region_code, status, created_by)
                VALUES (101, 'Video Gym', 'Seoul', 'seoul', 'active', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO gym_grades (id, gym_id, label, difficulty_order)
                VALUES (1001, 101, 'V1', 10)
                """);
        jdbcTemplate.update("""
                INSERT INTO reports (reporter_id, target_type, target_id, category, reason, status)
                VALUES (1, 'gym', 100, 'incorrect_info', '주소가 다릅니다.', 'pending')
                """);
        jdbcTemplate.update("""
                INSERT INTO videos (user_id, gym_id, gym_grade_id, title, gcs_path, recorded_date, status)
                VALUES (1, 101, 1001, 'Failed Video', 'videos/1/failed.mp4', ?, 'failed')
                """, LocalDate.now());
    }

    private String registerAndLoginAdmin() throws Exception {
        String email = "admin@hola.com";
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, "adminuser"))))
                .andExpect(status().isCreated());

        var user = userMapper.findByEmail(email);
        userMapper.updateRole(user.getId(), "ADMIN");
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }
}
