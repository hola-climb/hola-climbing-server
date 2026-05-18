package com.holaclimbing.server.domain.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.video.dto.request.CreateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification 도메인 통합 테스트.
 * 댓글·답글·좋아요·팔로우가 알림을 발생시키고, 수신자가 알림을 조회/읽음/삭제하는 전 구간을 검증한다.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/videos-schema.sql",
        "classpath:sql/notifications-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NotificationIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("알림 조회 실패 — 토큰 없이 호출하면 401")
    void getNotifications_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("신규 사용자는 알림이 없다")
    void newUser_hasNoNotifications() throws Exception {
        TestUser user = register("a@hola.com", "climberone");

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("댓글 — 다른 사용자가 댓글을 달면 영상 소유자에게 알림이 생긴다")
    void comment_notifiesVideoOwner() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser commenter = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());

        comment(commenter.token(), videoId, "great climb", null);

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("comment"))
                .andExpect(jsonPath("$.data.content[0].sender_id").value(commenter.id()))
                .andExpect(jsonPath("$.data.content[0].is_read").value(false));
    }

    @Test
    @DisplayName("댓글 — 자기 영상에 자기가 댓글을 달면 알림이 생기지 않는다")
    void commentOnOwnVideo_noNotification() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        long videoId = createVideo(owner.token());

        comment(owner.token(), videoId, "self comment", null);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("좋아요 — 다른 사용자가 좋아요하면 영상 소유자에게 알림이 생긴다")
    void like_notifiesVideoOwner() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser liker = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());

        mockMvc.perform(post("/api/videos/" + videoId + "/like")
                        .header("Authorization", "Bearer " + liker.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("like"));
    }

    @Test
    @DisplayName("팔로우 — 팔로우 당하면 대상에게 알림이 생긴다")
    void follow_notifiesFollowedUser() throws Exception {
        TestUser target = register("a@hola.com", "climberone");
        TestUser follower = register("b@hola.com", "climbertwo");

        mockMvc.perform(post("/api/users/" + target.id() + "/follow")
                        .header("Authorization", "Bearer " + follower.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + target.token()))
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("follow"))
                .andExpect(jsonPath("$.data.content[0].sender_id").value(follower.id()));
    }

    @Test
    @DisplayName("답글 — 답글이 달리면 부모 댓글 작성자에게 알림이 생긴다")
    void reply_notifiesParentCommentAuthor() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser replier = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());
        long parentCommentId = comment(owner.token(), videoId, "owner comment", null);

        comment(replier.token(), videoId, "nice point", parentCommentId);

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("reply"));
    }

    @Test
    @DisplayName("읽음 처리 — 읽음 처리하면 미읽음 수가 감소한다")
    void markRead_decreasesUnreadCount() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser commenter = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());
        comment(commenter.token(), videoId, "c", null);

        long notificationId = firstNotificationId(owner.token());
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("읽음 처리 — 다른 사람의 알림은 읽음 처리할 수 없다 (404 N001)")
    void markRead_othersNotification_returns404() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser commenter = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());
        comment(commenter.token(), videoId, "c", null);
        long notificationId = firstNotificationId(owner.token());

        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + commenter.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    @Test
    @DisplayName("전체 읽음 — 모든 알림이 읽음 처리된다")
    void markAllRead_success() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());
        comment(other.token(), videoId, "c1", null);
        mockMvc.perform(post("/api/videos/" + videoId + "/like")
                .header("Authorization", "Bearer " + other.token())).andExpect(status().isOk());

        mockMvc.perform(patch("/api/notifications/all/read")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unread_count").value(0));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("미읽음 필터 — unreadOnly=true면 읽은 알림은 제외된다")
    void unreadOnlyFilter() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());
        comment(other.token(), videoId, "c", null);

        long notificationId = firstNotificationId(owner.token());
        mockMvc.perform(patch("/api/notifications/" + notificationId + "/read")
                .header("Authorization", "Bearer " + owner.token())).andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").param("unreadOnly", "true")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data.total_elements").value(0));
        mockMvc.perform(get("/api/notifications").param("unreadOnly", "false")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data.total_elements").value(1));
    }

    @Test
    @DisplayName("알림 삭제 — 삭제하면 목록에서 사라진다")
    void deleteNotification_success() throws Exception {
        TestUser owner = register("a@hola.com", "climberone");
        TestUser other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner.token());
        comment(other.token(), videoId, "c", null);
        long notificationId = firstNotificationId(owner.token());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/notifications/" + notificationId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    @DisplayName("알림 설정 — 설정 행이 없으면 기본값(전부 ON)을 반환한다")
    void getSettings_defaults() throws Exception {
        TestUser user = register("a@hola.com", "climberone");

        mockMvc.perform(get("/api/notifications/settings")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notify_comment").value(true))
                .andExpect(jsonPath("$.data.notify_like").value(true));
    }

    @Test
    @DisplayName("알림 설정 변경 — 일부 토글을 끄면 그 값만 반영된다")
    void updateSettings_partial() throws Exception {
        TestUser user = register("a@hola.com", "climberone");

        mockMvc.perform(patch("/api/notifications/settings")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateNotificationSettingsRequest(
                                null, null, false, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notify_like").value(false))
                .andExpect(jsonPath("$.data.notify_comment").value(true));

        mockMvc.perform(get("/api/notifications/settings")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(jsonPath("$.data.notify_like").value(false));
    }

    // ===== helpers =====

    private record TestUser(Long id, String token) {
    }

    private TestUser register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());
        String token = dataOf(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD)))))
                .path("access_token").asText();
        return new TestUser(user.getId(), token);
    }

    private long createVideo(String token) throws Exception {
        return dataOf(mockMvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateVideoRequest(
                        null, "My Send", "desc", "V5", "gs://hola-bucket/v.mp4", null, 45, true))))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    private long comment(String token, long videoId, String content, Long parentId) throws Exception {
        return dataOf(mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateCommentRequest(content, parentId))))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    private long firstNotificationId(String token) throws Exception {
        return dataOf(mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token)))
                .path("content").get(0).path("id").asLong();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
