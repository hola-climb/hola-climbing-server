package com.holaclimbing.server.domain.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.video.dto.request.CreateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UploadUrlRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Video 도메인(피드·CRUD·좋아요·댓글) 통합 테스트.
 * JWT 인증 사용자가 영상을 등록/조회/수정/삭제하고 좋아요·댓글을 다는 전 구간을 검증한다.
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
class VideoIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("영상 등록 실패 — 토큰 없이 호출하면 401")
    void createVideo_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/videos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(videoRequest(true))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("영상 등록 성공 — 201, pending(분석 대기) 상태로 저장된다")
    void createVideo_success() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(videoRequest(true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.stream_url").exists())
                .andExpect(jsonPath("$.data.view_count").value(0))
                .andExpect(jsonPath("$.data.is_public").value(true));
    }

    @Test
    @DisplayName("업로드 URL 발급 — 인증 사용자는 Signed URL과 object_path를 받는다")
    void createUploadUrl_success() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/videos/upload-url")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UploadUrlRequest("send.mp4", 10_000_000L, "video/mp4"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upload_url").exists())
                .andExpect(jsonPath("$.data.object_path").value(org.hamcrest.Matchers.endsWith(".mp4")))
                .andExpect(jsonPath("$.data.expires_in").isNumber());
    }

    @Test
    @DisplayName("업로드 URL 발급 실패 — 토큰 없이 호출하면 401")
    void createUploadUrl_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/videos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UploadUrlRequest("send.mp4", 10_000_000L, "video/mp4"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("업로드 URL 발급 실패 — 지원하지 않는 확장자는 400 V004")
    void createUploadUrl_unsupportedFormat_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/videos/upload-url")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UploadUrlRequest("send.avi", 10_000_000L, "video/x-msvideo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("V004"));
    }

    @Test
    @DisplayName("업로드 URL 발급 실패 — 200MB 초과는 413 V002")
    void createUploadUrl_tooLarge_returns413() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/videos/upload-url")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UploadUrlRequest("send.mp4", 209_715_201L, "video/mp4"))))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("V002"));
    }

    @Test
    @DisplayName("피드 — 공개 영상만 노출되고 비공개는 제외된다")
    void getFeed_returnsPublicOnly() throws Exception {
        String token = register("a@hola.com", "climberone");
        createVideo(token, true);
        createVideo(token, true);
        createVideo(token, false);

        mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(2));
    }

    @Test
    @DisplayName("영상 상세 — 조회할 때마다 조회수가 증가한다")
    void getVideoDetail_incrementsViewCount() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.view_count").value(1));
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(jsonPath("$.data.view_count").value(2));
    }

    @Test
    @DisplayName("영상 상세 — 비공개 영상은 소유자만 조회 가능, 타인은 403 V006")
    void getVideoDetail_privateVideo_accessControl() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("영상 상세 — 없는 영상은 404 V001")
    void getVideoDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/videos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
    }

    @Test
    @DisplayName("영상 수정 — 소유자는 수정 가능, 타인은 403")
    void updateVideo_accessControl() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);

        var body = objectMapper.writeValueAsString(new UpdateVideoRequest("updated title", null, "V8", null));
        mockMvc.perform(patch("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("updated title"))
                .andExpect(jsonPath("$.data.grade").value("V8"));

        mockMvc.perform(patch("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("영상 삭제 — 소유자가 삭제하면 피드에서 사라진다")
    void deleteVideo_removesFromFeed() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);

        mockMvc.perform(delete("/api/videos/" + videoId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos"))
                .andExpect(jsonPath("$.data.total_elements").value(0));
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("좋아요 — 좋아요 후 상세에 is_liked=true, like_count 증가")
    void likeVideo_thenDetailShowsLiked() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String liker = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);

        mockMvc.perform(post("/api/videos/" + videoId + "/like").header("Authorization", "Bearer " + liker))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", "Bearer " + liker))
                .andExpect(jsonPath("$.data.like_count").value(1))
                .andExpect(jsonPath("$.data.is_liked").value(true));
    }

    @Test
    @DisplayName("좋아요 — 이미 좋아요한 영상은 400")
    void likeVideo_duplicate_returns400() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String liker = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);

        mockMvc.perform(post("/api/videos/" + videoId + "/like").header("Authorization", "Bearer " + liker))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/videos/" + videoId + "/like").header("Authorization", "Bearer " + liker))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("좋아요 취소 — 취소 후 like_count가 0으로 돌아온다")
    void unlikeVideo_success() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String liker = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);

        mockMvc.perform(post("/api/videos/" + videoId + "/like").header("Authorization", "Bearer " + liker))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/videos/" + videoId + "/like").header("Authorization", "Bearer " + liker))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(jsonPath("$.data.like_count").value(0));
    }

    @Test
    @DisplayName("댓글 작성 — 201, 영상 comment_count가 증가한다")
    void addComment_success() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String commenter = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);

        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header("Authorization", "Bearer " + commenter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("nice send!", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("nice send!"));

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(jsonPath("$.data.comment_count").value(1));
    }

    @Test
    @DisplayName("댓글 작성 — 잘못된 parentId는 400")
    void addComment_invalidParent_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);

        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("reply", 999999L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("댓글 목록 — 작성한 댓글이 목록에 나타난다")
    void getComments_list() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);
        addComment(token, videoId, "first");
        addComment(token, videoId, "second");

        mockMvc.perform(get("/api/videos/" + videoId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(2))
                .andExpect(jsonPath("$.data.content[0].content").value("first"));
    }

    @Test
    @DisplayName("댓글 삭제 — 작성자가 삭제하면 comment_count가 감소한다")
    void deleteComment_byOwner_success() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);
        long commentId = addComment(token, videoId, "to be deleted");

        mockMvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos/" + videoId + "/comments"))
                .andExpect(jsonPath("$.data.total_elements").value(0));
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(jsonPath("$.data.comment_count").value(0));
    }

    @Test
    @DisplayName("댓글 삭제 — 작성자가 아니면 403")
    void deleteComment_byOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);
        long commentId = addComment(owner, videoId, "owner comment");

        mockMvc.perform(delete("/api/comments/" + commentId).header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("분석 진행 상태 — 등록 직후 영상은 status=pending")
    void getStatus_returnsPending() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);

        mockMvc.perform(get("/api/videos/" + videoId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.video_id").value(videoId))
                .andExpect(jsonPath("$.data.status").value("pending"));
    }

    @Test
    @DisplayName("댓글 수정 — 작성자는 수정 가능, 타인은 403")
    void updateComment_accessControl() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);
        long commentId = addComment(owner, videoId, "before edit");

        var body = objectMapper.writeValueAsString(new UpdateCommentRequest("after edit"));
        mockMvc.perform(patch("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("after edit"));

        mockMvc.perform(patch("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ===== helpers =====

    private CreateVideoRequest videoRequest(boolean isPublic) {
        return new CreateVideoRequest(null, "My Send", "a clean ascent", "V5",
                "gs://hola-bucket/video.mp4", null, 45, isPublic);
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

    private long createVideo(String token, boolean isPublic) throws Exception {
        return dataOf(mockMvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(videoRequest(isPublic))))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    private long addComment(String token, long videoId, String content) throws Exception {
        return dataOf(mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateCommentRequest(content, null))))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
