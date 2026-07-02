package com.holaclimbing.server.domain.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import static com.holaclimbing.server.TestSignupRequests.signupRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/terms-data.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/videos-schema.sql",
        "classpath:sql/reports-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminReportIntegrationTest {

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
    @DisplayName("관리자 신고 - pending 목록 조회")
    void searchReports_pending_returnsRows() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long reporterId = userMapper.findByEmail("admin@hola.com").getId();
        long videoId = insertVideo(reporterId);
        insertReport(reporterId, "video", videoId, "spam");

        mockMvc.perform(get("/api/admin/reports")
                        .param("status", "pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].targetType").value("video"))
                .andExpect(jsonPath("$.data.content[0].status").value("pending"));
    }

    @Test
    @DisplayName("관리자 신고 - 영상 삭제 액션으로 해결 처리")
    void resolveReport_deleteVideo_softDeletesVideo() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long reporterId = userMapper.findByEmail("admin@hola.com").getId();
        long videoId = insertVideo(reporterId);
        long reportId = insertReport(reporterId, "video", videoId, "abuse");

        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\",\"resolutionAction\":\"delete_video\",\"reason\":\"정책 위반\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("resolved"));

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("관리자 신고 - 영상 삭제 시 같은 영상의 대기 신고도 함께 해결 처리하고 사용자 응답에서 제외")
    void resolveReport_deleteVideo_resolvesOtherPendingReportsAndHidesVideoFromUsers() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long firstReporterId = userMapper.findByEmail("admin@hola.com").getId();
        registerAndLoginUser("reporter2@hola.com", "reporter2");
        long secondReporterId = userMapper.findByEmail("reporter2@hola.com").getId();
        long videoId = insertVideo(firstReporterId);
        long firstReportId = insertReport(firstReporterId, "video", videoId, "abuse");
        long secondReportId = insertReport(secondReporterId, "video", videoId, "spam");

        mockMvc.perform(patch("/api/admin/reports/" + firstReportId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\",\"resolutionAction\":\"delete_video\",\"reason\":\"정책 위반\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("resolved"));

        String secondStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM reports WHERE id = ?", String.class, secondReportId);
        Long reviewedBy = jdbcTemplate.queryForObject(
                "SELECT reviewed_by FROM reports WHERE id = ?", Long.class, secondReportId);
        org.assertj.core.api.Assertions.assertThat(secondStatus).isEqualTo("resolved");
        org.assertj.core.api.Assertions.assertThat(reviewedBy).isEqualTo(firstReporterId);

        mockMvc.perform(get("/api/admin/reports")
                        .param("status", "pending")
                        .param("targetType", "video")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("관리자 신고 - 이미 삭제된 영상의 대기 신고도 닫을 수 있다")
    void resolveReport_deleteVideo_allowsAlreadyDeletedVideoTarget() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long firstReporterId = userMapper.findByEmail("admin@hola.com").getId();
        registerAndLoginUser("reporter4@hola.com", "reporter4");
        long secondReporterId = userMapper.findByEmail("reporter4@hola.com").getId();
        long videoId = insertVideo(firstReporterId);
        long firstReportId = insertReport(firstReporterId, "video", videoId, "abuse");
        long secondReportId = insertReport(secondReporterId, "video", videoId, "spam");
        jdbcTemplate.update("UPDATE videos SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?", videoId);

        mockMvc.perform(patch("/api/admin/reports/" + firstReportId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\",\"resolutionAction\":\"delete_video\",\"reason\":\"이미 삭제된 대상\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("resolved"));

        String secondStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM reports WHERE id = ?", String.class, secondReportId);
        org.assertj.core.api.Assertions.assertThat(secondStatus).isEqualTo("resolved");
    }

    @Test
    @DisplayName("관리자 신고 - 댓글 삭제 시 같은 댓글의 대기 신고도 함께 해결 처리하고 사용자 응답에서 제외")
    void resolveReport_deleteComment_resolvesOtherPendingReportsAndHidesCommentFromUsers() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long firstReporterId = userMapper.findByEmail("admin@hola.com").getId();
        registerAndLoginUser("reporter3@hola.com", "reporter3");
        long secondReporterId = userMapper.findByEmail("reporter3@hola.com").getId();
        long videoId = insertVideo(firstReporterId);
        long commentId = insertComment(firstReporterId, videoId);
        long firstReportId = insertReport(firstReporterId, "comment", commentId, "abuse");
        long secondReportId = insertReport(secondReporterId, "comment", commentId, "spam");

        mockMvc.perform(patch("/api/admin/reports/" + firstReportId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\",\"resolutionAction\":\"delete_comment\",\"reason\":\"정책 위반\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("resolved"));

        String secondStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM reports WHERE id = ?", String.class, secondReportId);
        Long reviewedBy = jdbcTemplate.queryForObject(
                "SELECT reviewed_by FROM reports WHERE id = ?", Long.class, secondReportId);
        org.assertj.core.api.Assertions.assertThat(secondStatus).isEqualTo("resolved");
        org.assertj.core.api.Assertions.assertThat(reviewedBy).isEqualTo(firstReporterId);

        mockMvc.perform(get("/api/videos/" + videoId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentCount").value(0));
    }

    @Test
    @DisplayName("관리자 신고 - 회원 정지 액션은 기존 토큰을 무효화한다")
    void resolveReport_suspendUser_revokesExistingToken() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long reporterId = userMapper.findByEmail("admin@hola.com").getId();
        String userToken = registerAndLoginUser("reported@hola.com", "reporteduser");
        long targetUserId = userMapper.findByEmail("reported@hola.com").getId();
        long reportId = insertReport(reporterId, "user", targetUserId, "abuse");

        Thread.sleep(1100);
        mockMvc.perform(patch("/api/admin/reports/" + reportId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\",\"resolutionAction\":\"suspend_user\",\"reason\":\"반복 괴롭힘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("resolved"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    private long insertVideo(long userId) {
        Long gymId = jdbcTemplate.queryForObject("""
                INSERT INTO gyms (name, address, region_code, status, created_by)
                VALUES ('Report Gym', 'Seoul', 'seoul', 'active', ?)
                RETURNING id
                """, Long.class, userId);
        Long gradeId = jdbcTemplate.queryForObject("""
                INSERT INTO gym_grades (gym_id, label, difficulty_order)
                VALUES (?, 'V1', 10)
                RETURNING id
                """, Long.class, gymId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO videos (user_id, gym_id, gym_grade_id, title, gcs_path, recorded_date, status, is_public)
                VALUES (?, ?, ?, 'Reported Video', 'videos/report.mp4', ?, 'done', TRUE)
                RETURNING id
                """, Long.class, userId, gymId, gradeId, LocalDate.now());
    }

    private long insertReport(long reporterId, String targetType, long targetId, String category) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO reports (reporter_id, target_type, target_id, category, reason)
                VALUES (?, ?, ?, ?, '운영 확인 필요')
                RETURNING id
                """, Long.class, reporterId, targetType, targetId, category);
    }

    private long insertComment(long userId, long videoId) {
        Long commentId = jdbcTemplate.queryForObject("""
                INSERT INTO comments (user_id, video_id, content)
                VALUES (?, ?, 'reported comment')
                RETURNING id
                """, Long.class, userId, videoId);
        jdbcTemplate.update("UPDATE videos SET comment_count = comment_count + 1 WHERE id = ?", videoId);
        return commentId;
    }

    private String registerAndLoginAdmin() throws Exception {
        String email = "admin@hola.com";
        registerAndLoginUser(email, "adminuser");
        var user = userMapper.findByEmail(email);
        userMapper.updateRole(user.getId(), "ADMIN");
        return login(email);
    }

    private String registerAndLoginUser(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());

        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());
        return login(email);
    }

    private String login(String email) throws Exception {
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
