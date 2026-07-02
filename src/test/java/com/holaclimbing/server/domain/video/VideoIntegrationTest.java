package com.holaclimbing.server.domain.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import static com.holaclimbing.server.TestSignupRequests.signupRequest;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.WithdrawRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.video.dto.request.CreateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UploadUrlRequest;
import com.holaclimbing.server.domain.video.mapper.VideoMapper;
import com.holaclimbing.server.infrastructure.ai.AnalysisProgress;
import com.holaclimbing.server.infrastructure.ai.AnalysisStage;
import com.holaclimbing.server.infrastructure.ai.AnalysisStatusStore;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        "classpath:sql/terms-data.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/favorites-schema.sql",
        "classpath:sql/videos-schema.sql",
        "classpath:sql/notifications-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VideoIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final LocalDate RECORDED_DATE = LocalDate.of(2026, 6, 3);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AnalysisStatusStore analysisStatusStore;

    @Test
    @DisplayName("영상 등록 실패 — 자기 소유 prefix가 아닌 objectPath면 403 FORBIDDEN")
    void createVideo_foreignObjectPath_returns403() throws Exception {
        String token = register("a@hola.com", "climberone");
        var bad = new CreateVideoRequest(1L, "stolen", null, 1003L,
                "videos/uploads/9999/owned-by-someone-else.mp4", null, 45, RECORDED_DATE, true);
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

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

        var req = new CreateVideoRequest(1L, "My Send", "a clean ascent", 1003L,
                ownedObjectPath(token), null, 45, RECORDED_DATE, true);
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.gymId").value(1))
                .andExpect(jsonPath("$.data.gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.gymGradeId").doesNotExist())
                .andExpect(jsonPath("$.data.grade").doesNotExist())
                .andExpect(jsonPath("$.data.gymGrade.id").value(1003))
                .andExpect(jsonPath("$.data.gymGrade.gymId").value(1))
                .andExpect(jsonPath("$.data.gymGrade.label").value("빨강"))
                .andExpect(jsonPath("$.data.gymGrade.colorHex").doesNotExist())
                .andExpect(jsonPath("$.data.gymGrade.difficultyOrder").value(30))
                .andExpect(jsonPath("$.data.streamUrl").exists())
                .andExpect(jsonPath("$.data.recordedDate").value("2026-06-03"))
                .andExpect(jsonPath("$.data.viewCount").value(0))
                .andExpect(jsonPath("$.data.isPublic").value(true));
    }

    @Test
    @DisplayName("영상 등록 성공 — 사용자가 입력한 촬영일을 저장하고 반환한다")
    void createVideo_withRecordedDate_returnsRecordedDate() throws Exception {
        String token = register("a@hola.com", "climberone");

        var body = java.util.Map.of(
                "gymId", 1,
                "gymGradeId", 1003,
                "objectPath", ownedObjectPath(token),
                "title", "MoonBoard session",
                "recordedDate", "2026-06-03",
                "isPublic", true
        );
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recordedDate").value("2026-06-03"));
    }

    @Test
    @DisplayName("영상 등록 실패 — 촬영일(recordedDate)을 누락하면 400")
    void createVideo_withoutRecordedDate_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        var body = java.util.Map.of(
                "gymId", 1,
                "gymGradeId", 1003,
                "objectPath", ownedObjectPath(token),
                "title", "MoonBoard session",
                "isPublic", true
        );
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("영상 등록 실패 — gymId를 누락하면 400")
    void createVideo_withoutGymId_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        var body = java.util.Map.of(
                "gymGradeId", 1003,
                "objectPath", ownedObjectPath(token),
                "recordedDate", "2026-06-03",
                "isPublic", true
        );
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("영상 등록 실패 — gymGradeId를 누락하면 400")
    void createVideo_withoutGymGradeId_returns400() throws Exception {
        String token = register("a@hola.com", "climberone");

        var body = java.util.Map.of(
                "gymId", 1,
                "objectPath", ownedObjectPath(token),
                "recordedDate", "2026-06-03",
                "isPublic", true
        );
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("영상 등록 실패 — 다른 암장의 난이도를 선택하면 G005")
    void createVideo_gradeFromOtherGym_returnsG005() throws Exception {
        String token = register("a@hola.com", "climberone");

        var body = java.util.Map.of(
                "gymId", 1,
                "gymGradeId", 1004,
                "objectPath", ownedObjectPath(token),
                "recordedDate", "2026-06-03",
                "isPublic", true
        );
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("G005"));
    }

    @Test
    @DisplayName("영상 등록 실패 — 비활성 난이도를 선택하면 G005")
    void createVideo_inactiveGrade_returnsG005() throws Exception {
        String token = register("a@hola.com", "climberone");

        var body = java.util.Map.of(
                "gymId", 2,
                "gymGradeId", 1006,
                "objectPath", ownedObjectPath(token),
                "recordedDate", "2026-06-03",
                "isPublic", true
        );
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("G005"));
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
                .andExpect(jsonPath("$.data.uploadUrl").exists())
                .andExpect(jsonPath("$.data.objectPath").value(org.hamcrest.Matchers.endsWith(".mp4")))
                .andExpect(jsonPath("$.data.expiresIn").isNumber());
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
    @DisplayName("썸네일 업로드 — multipart 이미지를 public thumbnail bucket에 업로드하고 public URL을 반환한다")
    void uploadThumbnail_success() throws Exception {
        String token = register("thumbnail@hola.com", "thumbuser");
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumbnail.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-jpeg-thumbnail".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/videos/thumbnail")
                        .file(image)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailPath").value(org.hamcrest.Matchers.containsString(
                        "videos/thumbnails/")))
                .andExpect(jsonPath("$.data.thumbnailPath").value(org.hamcrest.Matchers.endsWith(".jpg")))
                .andExpect(jsonPath("$.data.thumbnailUrl").value(org.hamcrest.Matchers.containsString(
                        "https://storage.googleapis.com/hola-climbing-thumbnails-public/videos/thumbnails/")))
                .andExpect(jsonPath("$.data.thumbnailUrl").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("X-Goog-Signature="))));
    }

    @Test
    @DisplayName("영상 등록 성공 — 서버가 업로드한 썸네일 경로를 저장하고 public thumbnail URL을 반환한다")
    void createVideo_withUploadedThumbnail_returnsThumbnailUrl() throws Exception {
        String token = register("thumb-video@hola.com", "thumbvideo");
        String thumbnailPath = uploadedThumbnailPath(token);

        var req = new CreateVideoRequest(1L, "My Send", "with thumbnail", 1003L,
                ownedObjectPath(token), thumbnailPath, 45, RECORDED_DATE, true);

        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.thumbnailPath").value(thumbnailPath))
                .andExpect(jsonPath("$.data.thumbnailUrl").value(org.hamcrest.Matchers.containsString(
                        "https://storage.googleapis.com/hola-climbing-thumbnails-public/videos/thumbnails/")))
                .andExpect(jsonPath("$.data.thumbnailUrl").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("X-Goog-Signature="))));
    }

    @Test
    @DisplayName("영상 등록 실패 — 외부 썸네일 URL은 저장할 수 없다")
    void createVideo_externalThumbnailUrl_returns403() throws Exception {
        String token = register("external-thumb@hola.com", "externalthumb");
        var req = new CreateVideoRequest(1L, "bad thumbnail", null, 1003L,
                ownedObjectPath(token), "https://storage.googleapis.com/bucket/external.jpg",
                45, RECORDED_DATE, true);

        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    @DisplayName("영상 등록 실패 — 다른 사용자 썸네일 prefix는 저장할 수 없다")
    void createVideo_foreignThumbnailPath_returns403() throws Exception {
        String token = register("foreign-thumb@hola.com", "foreignthumb");
        var req = new CreateVideoRequest(1L, "bad thumbnail", null, 1003L,
                ownedObjectPath(token), "videos/thumbnails/9999/other.jpg",
                45, RECORDED_DATE, true);

        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    @DisplayName("피드 — 공개 영상만 노출되고 비공개는 제외된다 (커서)")
    void getFeed_returnsPublicOnly() throws Exception {
        String token = register("a@hola.com", "climberone");
        long userId = userMapper.findByEmail("a@hola.com").getId();
        jdbcTemplate.update("UPDATE users SET profile_image = ? WHERE id = ?",
                "profile-images/" + userId + "/feed.jpg", userId);
        createVideo(token, true);
        createVideo(token, true);
        createVideo(token, false);

        mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].nickname").value("climberone"))
                .andExpect(jsonPath("$.data.content[0].profileImage").isString())
                .andExpect(jsonPath("$.data.content[0].profileImage").value(org.hamcrest.Matchers.containsString(
                        "profile-images/" + userId + "/feed.jpg")))
                .andExpect(jsonPath("$.data.content[0].profileImage").value(org.hamcrest.Matchers.containsString(
                        "X-Goog-Signature=")))
                .andExpect(jsonPath("$.data.content[0].gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.content[0].gymGrade.id").value(1003))
                .andExpect(jsonPath("$.data.content[0].gymGrade.label").value("빨강"))
                .andExpect(jsonPath("$.data.content[0].grade").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("피드·암장 영상 목록 — failed 영상도 노출하고 status를 내려준다")
    void videoLists_includeFailedVideos() throws Exception {
        String token = register("failed-video@hola.com", "failedclip");
        long videoId = createVideo(token, true);
        videoMapper.updateStatus(videoId, "failed");

        var feed = dataOf(mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1)));
        org.assertj.core.api.Assertions.assertThat(feed.path("content").get(0).path("id").asLong())
                .isEqualTo(videoId);
        org.assertj.core.api.Assertions.assertThat(feed.path("content").get(0).path("status").asText())
                .isEqualTo("failed");

        var gymVideos = dataOf(mockMvc.perform(get("/api/gyms/1/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1)));
        org.assertj.core.api.Assertions.assertThat(gymVideos.path("content").get(0).path("id").asLong())
                .isEqualTo(videoId);
        org.assertj.core.api.Assertions.assertThat(gymVideos.path("content").get(0).path("status").asText())
                .isEqualTo("failed");
    }

    @Test
    @DisplayName("피드·암장 영상 목록 — 탈퇴한 작성자의 영상은 제외된다")
    void videoLists_excludeWithdrawnUploaderVideos() throws Exception {
        String activeUploader = register("active-feed@hola.com", "activefeed");
        String withdrawnUploader = register("withdrawn-feed@hola.com", "withdrawnfeed");
        long withdrawnVideoId = createVideo(withdrawnUploader, true);
        long activeVideoId = createVideo(activeUploader, true);

        withdraw(withdrawnUploader);

        var feed = dataOf(mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false)));
        org.assertj.core.api.Assertions.assertThat(feed.path("content").get(0).path("id").asLong())
                .isEqualTo(activeVideoId)
                .isNotEqualTo(withdrawnVideoId);

        var gymVideos = dataOf(mockMvc.perform(get("/api/gyms/1/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1)));
        org.assertj.core.api.Assertions.assertThat(gymVideos.path("content").get(0).path("id").asLong())
                .isEqualTo(activeVideoId)
                .isNotEqualTo(withdrawnVideoId);
    }

    @Test
    @DisplayName("피드·상세·분석 — 정지된 작성자의 영상은 사용자 API에서 숨긴다")
    void videoApis_excludeSuspendedUploaderVideos() throws Exception {
        String activeUploader = register("active-suspended-feed@hola.com", "activesuspendedfeed");
        String suspendedUploader = register("suspended-feed@hola.com", "suspendedfeed");
        long suspendedUserId = userMapper.findByEmail("suspended-feed@hola.com").getId();
        long suspendedVideoId = createVideo(suspendedUploader, true);
        long activeVideoId = createVideo(activeUploader, true);

        userMapper.updateStatus(suspendedUserId, "SUSPENDED");

        var feed = dataOf(mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1)));
        org.assertj.core.api.Assertions.assertThat(feed.path("content").get(0).path("id").asLong())
                .isEqualTo(activeVideoId)
                .isNotEqualTo(suspendedVideoId);

        mockMvc.perform(get("/api/videos/" + suspendedVideoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
        mockMvc.perform(get("/api/videos/" + suspendedVideoId + "/analysis"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
    }

    @Test
    @DisplayName("피드·암장 영상 목록·상세 — 닫힌 암장의 영상은 사용자 API에서 숨긴다")
    void videoApis_excludeClosedGymVideos() throws Exception {
        String token = register("closed-gym-video@hola.com", "closedgymvideo");
        long videoId = createVideo(token, true);

        jdbcTemplate.update("UPDATE gyms SET status = 'closed' WHERE id = ?", 1L);

        mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mockMvc.perform(get("/api/gyms/1/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
    }

    @Test
    @DisplayName("피드 — 커서로 다음 페이지를 중복·누락 없이 가져온다")
    void getFeed_cursorPagination() throws Exception {
        String token = register("a@hola.com", "climberone");
        for (int i = 0; i < 3; i++) {
            createVideo(token, true);
        }

        // size=2 → 2건 + hasNext=true + nextCursor 발급
        var firstPage = dataOf(mockMvc.perform(get("/api/videos").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true)));
        long firstId0 = firstPage.path("content").get(0).path("id").asLong();
        long firstId1 = firstPage.path("content").get(1).path("id").asLong();
        String cursor = firstPage.path("nextCursor").asText();

        // 두 번째 페이지 — 남은 1건, hasNext=false, 첫 페이지와 겹치지 않음
        var secondPage = dataOf(mockMvc.perform(get("/api/videos").param("size", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false)));
        long secondId = secondPage.path("content").get(0).path("id").asLong();
        org.assertj.core.api.Assertions.assertThat(secondId)
                .isNotIn(firstId0, firstId1)
                .isLessThan(firstId1);   // id 내림차순이므로 다음 페이지 id가 더 작다
    }

    @Test
    @DisplayName("피드 — nextCursor 파라미터로도 다음 페이지를 조회한다")
    void getFeed_acceptsNextCursorParameter() throws Exception {
        String token = register("next-cursor@hola.com", "nextcursor");
        for (int i = 0; i < 3; i++) {
            createVideo(token, true);
        }

        var firstPage = dataOf(mockMvc.perform(get("/api/videos").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true)));

        var secondPage = dataOf(mockMvc.perform(get("/api/videos")
                        .param("size", "2")
                        .param("nextCursor", firstPage.path("nextCursor").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false)));

        org.assertj.core.api.Assertions.assertThat(secondPage.path("content").get(0).path("id").asLong())
                .isNotIn(firstPage.path("content").get(0).path("id").asLong(),
                        firstPage.path("content").get(1).path("id").asLong());
    }

    @Test
    @DisplayName("내 영상 목록 — 촬영일 최신순으로 반환한다")
    void getFeed_withUserIdOrdersByRecordedDateDesc() throws Exception {
        String token = register("my-videos@hola.com", "myvideos");
        long userId = userMapper.findByEmail("my-videos@hola.com").getId();
        long newerRecorded = createVideoOn(token, true, LocalDate.of(2026, 6, 3));
        createVideoOn(token, true, LocalDate.of(2026, 6, 1));

        var page = dataOf(mockMvc.perform(get("/api/videos")
                        .param("userId", Long.toString(userId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].recordedDate").value("2026-06-03")));
        org.assertj.core.api.Assertions.assertThat(page.path("content").get(0).path("id").asLong())
                .isEqualTo(newerRecorded);
    }

    @Test
    @DisplayName("내 영상 목록 — 커서 페이지도 촬영일 최신순을 유지한다")
    void getFeed_withUserIdCursorOrdersByRecordedDateDesc() throws Exception {
        String token = register("my-cursor@hola.com", "mycursor");
        long userId = userMapper.findByEmail("my-cursor@hola.com").getId();
        createVideoOn(token, true, LocalDate.of(2026, 6, 3));
        createVideoOn(token, true, LocalDate.of(2026, 6, 1));
        createVideoOn(token, true, LocalDate.of(2026, 6, 2));

        var firstPage = dataOf(mockMvc.perform(get("/api/videos")
                        .param("userId", Long.toString(userId))
                        .param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].recordedDate").value("2026-06-03"))
                .andExpect(jsonPath("$.data.content[1].recordedDate").value("2026-06-02"))
                .andExpect(jsonPath("$.data.hasNext").value(true)));

        var secondPage = dataOf(mockMvc.perform(get("/api/videos")
                        .param("userId", Long.toString(userId))
                        .param("size", "2")
                        .param("cursor", firstPage.path("nextCursor").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].recordedDate").value("2026-06-01"))
                .andExpect(jsonPath("$.data.hasNext").value(false)));

        org.assertj.core.api.Assertions.assertThat(secondPage.path("content").get(0).path("id").asLong())
                .isNotIn(firstPage.path("content").get(0).path("id").asLong(),
                        firstPage.path("content").get(1).path("id").asLong());
    }

    @Test
    @DisplayName("피드 — 잘못된 커서는 400")
    void getFeed_invalidCursor_returns400() throws Exception {
        mockMvc.perform(get("/api/videos").param("cursor", "!!!not-base64!!!"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("피드 — recordedDate로 해당 촬영일 영상만 필터한다")
    void getFeed_filtersByRecordedDate() throws Exception {
        String token = register("a@hola.com", "climberone");
        long onJun3 = createVideoOn(token, true, LocalDate.of(2026, 6, 3));
        createVideoOn(token, true, LocalDate.of(2026, 6, 1));

        var page = dataOf(mockMvc.perform(get("/api/videos").param("recordedDate", "2026-06-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].recordedDate").value("2026-06-03")));
        org.assertj.core.api.Assertions.assertThat(page.path("content").get(0).path("id").asLong())
                .isEqualTo(onJun3);
    }

    @Test
    @DisplayName("피드 — 인증 사용자는 recordedDate 조회에서 자기 비공개 영상도 본다")
    void getFeed_filtersByRecordedDate_includesOwnPrivateVideo() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long ownPrivate = createVideoOn(owner, false, LocalDate.of(2026, 6, 3));
        createVideoOn(other, false, LocalDate.of(2026, 6, 3));
        createVideoOn(owner, false, LocalDate.of(2026, 6, 1));

        var page = dataOf(mockMvc.perform(get("/api/videos")
                        .param("recordedDate", "2026-06-03")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].recordedDate").value("2026-06-03")));
        org.assertj.core.api.Assertions.assertThat(page.path("content").get(0).path("id").asLong())
                .isEqualTo(ownPrivate);
    }

    @Test
    @DisplayName("피드 — 잘못된 형식의 recordedDate는 400")
    void getFeed_invalidRecordedDate_returns400() throws Exception {
        mockMvc.perform(get("/api/videos").param("recordedDate", "06-03-2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("영상 기반 암장 추천 — 공개 분석 영상의 기술·동적 성향이 가까운 암장을 우선하고 추천 이유를 내려준다")
    void getVideoGymRecommendations_ranksByTechniqueAndDynamicReasons() throws Exception {
        String viewer = register("viewer@hola.com", "viewer");
        String owner = register("style-owner@hola.com", "styleowner");
        long viewerId = userMapper.findByEmail("viewer@hola.com").getId();
        jdbcTemplate.update("INSERT INTO favorites (user_id, gym_id) VALUES (?, ?)", viewerId, 2L);

        long seed = createVideoAtGym(owner, true, 1L, 1003L);
        insertAnalysis(seed, List.of("heel_hook", "dyno"), true);

        long hongdae1 = createVideoAtGym(owner, true, 2L, 1005L);
        long hongdae2 = createVideoAtGym(owner, true, 2L, 1005L);
        insertAnalysis(hongdae1, List.of("heel_hook", "dyno"), true);
        insertAnalysis(hongdae2, List.of("heel_hook", "toe_hook"), true);

        long pangyo1 = createVideoAtGym(owner, true, 3L, 1008L);
        long pangyo2 = createVideoAtGym(owner, true, 3L, 1008L);
        insertAnalysis(pangyo1, List.of("slab", "crimp"), false);
        insertAnalysis(pangyo2, List.of("mantle"), false);

        mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seed)
                        .param("lat", "37.4979")
                        .param("lng", "127.0276")
                        .param("radius", "30")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].source").value("video_style_match"))
                .andExpect(jsonPath("$.data[0].isFavorite").value(true))
                .andExpect(jsonPath("$.data[0].reasons").value(org.hamcrest.Matchers.containsInAnyOrder("technique", "dynamic")))
                .andExpect(jsonPath("$.data[0].techniqueScore").value(org.hamcrest.Matchers.greaterThan(0.6)))
                .andExpect(jsonPath("$.data[0].dynamicScore").value(org.hamcrest.Matchers.greaterThan(0.1)))
                .andExpect(jsonPath("$.data[1].id").value(3));
    }

    @Test
    @DisplayName("영상 기반 암장 추천 — 암장별 공개 분석 영상이 부족하면 거리 기반 fallback으로 내려준다")
    void getVideoGymRecommendations_fallsBackToNearbyWhenGymAnalysisIsSparse() throws Exception {
        String owner = register("fallback-owner@hola.com", "fallbackowner");
        long seed = createVideoAtGym(owner, true, 1L, 1003L);
        insertAnalysis(seed, List.of("heel_hook", "dyno"), true);
        long onlyOneAnalyzed = createVideoAtGym(owner, true, 2L, 1005L);
        insertAnalysis(onlyOneAnalyzed, List.of("heel_hook", "dyno"), true);

        mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seed)
                        .param("lat", "37.4979")
                        .param("lng", "127.0276")
                        .param("radius", "30")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].source").value("nearby"))
                .andExpect(jsonPath("$.data[0].reasons").value(org.hamcrest.Matchers.hasItem("distance")))
                .andExpect(jsonPath("$.data[0].similarityScore").doesNotExist());
    }

    @Test
    @DisplayName("영상 기반 암장 추천 — 기준 영상이 비공개면 추천을 조회할 수 없다")
    void getVideoGymRecommendations_privateSeedVideoReturns403() throws Exception {
        String owner = register("private-owner@hola.com", "privateowner");
        long seed = createVideoAtGym(owner, false, 1L, 1003L);
        insertAnalysis(seed, List.of("heel_hook", "dyno"), true);

        mockMvc.perform(get("/api/videos/{videoId}/recommendations/gyms", seed)
                        .param("lat", "37.4979")
                        .param("lng", "127.0276")
                        .param("radius", "30")
                        .param("size", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("영상 상세 — 조회할 때마다 조회수가 증가한다")
    void getVideoDetail_incrementsViewCount() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);

        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.viewCount").value(1));
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(jsonPath("$.data.viewCount").value(2));
    }

    @Test
    @DisplayName("영상 상세 — 소유자가 자기 영상을 조회해도 조회수가 증가하지 않는다")
    void getVideoDetail_byOwnerDoesNotIncrementViewCount() throws Exception {
        String owner = register("owner@hola.com", "ownerclimber");
        long videoId = createVideo(owner, true);

        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(0));
        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewCount").value(0));
    }

    @Test
    @DisplayName("영상 상세 — 인증 사용자가 조회하면 추천용 조회 이력을 기록한다")
    void getVideoDetail_recordsViewerInteraction() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String viewer = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);
        long viewerId = userMapper.findByEmail("b@hola.com").getId();

        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk());

        Integer viewedCount = jdbcTemplate.queryForObject("""
                SELECT viewed_count
                FROM user_video_interactions
                WHERE user_id = ? AND video_id = ?
                """, Integer.class, viewerId, videoId);

        org.assertj.core.api.Assertions.assertThat(viewedCount).isEqualTo(1);
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
    @DisplayName("영상 상세·분석·액션 — 조회자가 작성자를 차단하면 직접 접근도 막힌다")
    void videoAccess_viewerBlockedAuthor_returns403() throws Exception {
        String owner = register("blocked-video-owner@hola.com", "blockedvideoowner");
        String viewer = register("blocked-video-viewer@hola.com", "blockedvideoviewer");
        long ownerId = userMapper.findByEmail("blocked-video-owner@hola.com").getId();
        long videoId = createVideo(owner, true);

        mockMvc.perform(post("/api/users/" + ownerId + "/block")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
        mockMvc.perform(get("/api/videos/" + videoId + "/analysis")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
        mockMvc.perform(post("/api/videos/" + videoId + "/like")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header("Authorization", "Bearer " + viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("blocked comment", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("영상 상세 — 작성자가 조회자를 차단해도 직접 접근을 막는다")
    void videoAccess_authorBlockedViewer_returns403() throws Exception {
        String owner = register("author-block-owner@hola.com", "authorblockowner");
        String viewer = register("author-block-viewer@hola.com", "authorblockviewer");
        long viewerId = userMapper.findByEmail("author-block-viewer@hola.com").getId();
        long videoId = createVideo(owner, true);

        mockMvc.perform(post("/api/users/" + viewerId + "/block")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("피드 — 업로더가 조회자를 차단하면 목록에서도 제외된다")
    void getFeed_excludesUploaderWhoBlockedViewer() throws Exception {
        String owner = register("feed-block-owner@hola.com", "feedblockowner");
        String viewer = register("feed-block-viewer@hola.com", "feedblockviewer");
        long viewerId = userMapper.findByEmail("feed-block-viewer@hola.com").getId();
        createVideo(owner, true);

        mockMvc.perform(post("/api/users/" + viewerId + "/block")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
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

        var body = objectMapper.writeValueAsString(new UpdateVideoRequest("updated title", null, null));
        mockMvc.perform(patch("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("updated title"))
                .andExpect(jsonPath("$.data.gymGrade.label").value("빨강"))
                .andExpect(jsonPath("$.data.grade").doesNotExist());

        mockMvc.perform(patch("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("영상 수정 — 암장, 난이도, 촬영일을 함께 수정할 수 있다")
    void updateVideo_updatesGymGradeAndRecordedDate() throws Exception {
        String owner = register("a@hola.com", "climberone");
        long videoId = createVideo(owner, true);

        var body = objectMapper.writeValueAsString(Map.of(
                "gymId", 2,
                "gymGradeId", 1004,
                "recordedDate", "2026-06-01"
        ));

        mockMvc.perform(patch("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gymId").value(2))
                .andExpect(jsonPath("$.data.gymGrade.id").value(1004))
                .andExpect(jsonPath("$.data.gymGrade.gymId").value(2))
                .andExpect(jsonPath("$.data.gymGrade.label").value("노랑"))
                .andExpect(jsonPath("$.data.recordedDate").value("2026-06-01"));
    }

    @Test
    @DisplayName("영상 수정 실패 — 암장을 수정할 때 난이도를 누락하면 400")
    void updateVideo_gymWithoutGrade_returns400() throws Exception {
        String owner = register("a@hola.com", "climberone");
        long videoId = createVideo(owner, true);

        var body = objectMapper.writeValueAsString(Map.of("gymId", 2));

        mockMvc.perform(patch("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("영상 삭제 — 소유자가 삭제하면 피드에서 사라진다")
    void deleteVideo_removesFromFeed() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);

        mockMvc.perform(delete("/api/videos/" + videoId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos"))
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
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
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.isLiked").value(true));
    }

    @Test
    @DisplayName("좋아요 실패 — 비공개 영상은 타인이 좋아요할 수 없다")
    void likeVideo_privateVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(post("/api/videos/" + videoId + "/like")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
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
                .andExpect(jsonPath("$.data.likeCount").value(0));
    }

    @Test
    @DisplayName("좋아요 취소 실패 — 비공개 영상은 타인이 좋아요 취소할 수 없다")
    void unlikeVideo_privateVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(delete("/api/videos/" + videoId + "/like")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
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
                .andExpect(jsonPath("$.data.commentCount").value(1));
    }

    @Test
    @DisplayName("댓글 작성 실패 — 비공개 영상은 타인이 댓글을 달 수 없다")
    void addComment_privateVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(post("/api/videos/" + videoId + "/comments")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("nice send!", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
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
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].content").value("first"));
    }

    @Test
    @DisplayName("댓글 목록 — 작성자 프로필 이미지를 함께 반환한다")
    void getComments_includesAuthorProfileImage() throws Exception {
        String token = register("profile-comment@hola.com", "profilecommenter");
        long userId = userMapper.findByEmail("profile-comment@hola.com").getId();
        jdbcTemplate.update("UPDATE users SET profile_image = ? WHERE id = ?",
                "profile-images/" + userId + "/seed.jpg", userId);
        long videoId = createVideo(token, true);
        addComment(token, videoId, "profile image comment");

        mockMvc.perform(get("/api/videos/" + videoId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("profile image comment"))
                .andExpect(jsonPath("$.data.content[0].nickname").value("profilecommenter"))
                .andExpect(jsonPath("$.data.content[0].profileImage").isString())
                .andExpect(jsonPath("$.data.content[0].profileImage").value(org.hamcrest.Matchers.containsString(
                        "profile-images/" + userId + "/seed.jpg")))
                .andExpect(jsonPath("$.data.content[0].profileImage").value(org.hamcrest.Matchers.containsString(
                        "X-Goog-Signature=")));
    }

    @Test
    @DisplayName("댓글 목록 실패 — 비공개 영상 댓글은 타인이 조회할 수 없다")
    void getComments_privateVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);
        addComment(owner, videoId, "private comment");

        mockMvc.perform(get("/api/videos/" + videoId + "/comments")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("댓글 목록 — 정지된 작성자의 댓글은 카운트와 목록에서 제외된다")
    void getComments_excludesSuspendedAuthors() throws Exception {
        String owner = register("comment-owner@hola.com", "commentowner");
        String commenter = register("comment-suspended@hola.com", "commentsuspended");
        long commenterId = userMapper.findByEmail("comment-suspended@hola.com").getId();
        long videoId = createVideo(owner, true);
        long commentId = addComment(commenter, videoId, "hide me after suspension");

        userMapper.updateStatus(commenterId, "SUSPENDED");

        var comments = dataOf(mockMvc.perform(get("/api/videos/" + videoId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0)));
        org.assertj.core.api.Assertions.assertThat(comments.path("content").findValuesAsText("id"))
                .doesNotContain(String.valueOf(commentId));
    }

    @Test
    @DisplayName("댓글 목록 — 댓글 작성자가 조회자를 차단하면 목록에서 제외된다")
    void getComments_excludesAuthorWhoBlockedViewer() throws Exception {
        String owner = register("comment-block-owner@hola.com", "commentblockowner");
        String commenter = register("comment-block-author@hola.com", "commentblockauthor");
        String viewer = register("comment-block-viewer@hola.com", "commentblockviewer");
        long viewerId = userMapper.findByEmail("comment-block-viewer@hola.com").getId();
        long videoId = createVideo(owner, true);
        addComment(commenter, videoId, "hidden after author block");

        mockMvc.perform(post("/api/users/" + viewerId + "/block")
                        .header("Authorization", "Bearer " + commenter))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/videos/" + videoId + "/comments")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
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
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/videos/" + videoId))
                .andExpect(jsonPath("$.data.commentCount").value(0));
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
                .andExpect(jsonPath("$.data.videoId").value(videoId))
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.progress").value(0))
                .andExpect(jsonPath("$.data.stage").value("queued"));
    }

    @Test
    @DisplayName("분석 진행 상태 — 워커 PROCESSING 이벤트가 있으면 analyzing/progress/stage로 반환한다")
    void getStatus_whenProcessingProgressExists_returnsAnalyzingProgress() throws Exception {
        String token = register("a@hola.com", "climberone");
        long videoId = createVideo(token, true);
        analysisStatusStore.save(AnalysisProgress.of(videoId, AnalysisStage.PROCESSING, "포즈 추정 완료"));

        mockMvc.perform(get("/api/videos/" + videoId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoId").value(videoId))
                .andExpect(jsonPath("$.data.status").value("analyzing"))
                .andExpect(jsonPath("$.data.progress").value(70))
                .andExpect(jsonPath("$.data.stage").value("pose_estimation"))
                .andExpect(jsonPath("$.data.estimatedSecondsRemaining").doesNotExist());
    }

    @Test
    @DisplayName("분석 진행 상태 실패 — 비공개 영상 상태는 타인이 조회할 수 없다")
    void getStatus_privateVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(get("/api/videos/" + videoId + "/status")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("분석 진행 SSE 실패 — 비공개 영상 진행률은 타인이 구독할 수 없다")
    void streamAnalysisProgress_privateVideoByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(get("/api/videos/" + videoId + "/analysis/stream")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
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

    @Test
    @DisplayName("댓글 수정/삭제 — 공개 영상에 댓글을 쓴 사용자도 비공개 전환 후에는 수정/삭제할 수 없다")
    void updateOrDeleteComment_afterVideoBecomesPrivateByOther_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String commenter = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);
        long commentId = addComment(commenter, videoId, "public comment");

        var makePrivate = objectMapper.writeValueAsString(new UpdateVideoRequest(null, null, false));
        mockMvc.perform(patch("/api/videos/" + videoId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(makePrivate))
                .andExpect(status().isOk());

        var edit = objectMapper.writeValueAsString(new UpdateCommentRequest("edited after private"));
        mockMvc.perform(patch("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + commenter)
                        .contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", "Bearer " + commenter))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("암장 영상 목록 — 해당 암장의 공개 영상만 반환한다")
    void getGymVideos_returnsGymVideos() throws Exception {
        String token = register("a@hola.com", "climberone");
        for (int i = 0; i < 2; i++) {
            var gymVideo = new CreateVideoRequest(1L, "gym clip", "desc", 1002L,
                    ownedObjectPath(token), null, 30, RECORDED_DATE, true);
            mockMvc.perform(post("/api/videos")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(gymVideo)))
                    .andExpect(status().isCreated());
        }
        var otherGymVideo = new CreateVideoRequest(2L, "other gym clip", "desc", 1004L,
                ownedObjectPath(token), null, 30, RECORDED_DATE, true);
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otherGymVideo)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/gyms/1/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.content[0].gymGrade.gymId").value(1))
                .andExpect(jsonPath("$.data.content[0].gymGrade.label").value("파랑"))
                .andExpect(jsonPath("$.data.content[0].grade").doesNotExist());
    }

    @Test
    @DisplayName("암장 영상 목록 — gymGradeId로 난이도를 필터한다")
    void getGymVideos_filtersByGymGradeId() throws Exception {
        String token = register("gym-grade-filter@hola.com", "gradefilter");
        var blueVideo = new CreateVideoRequest(1L, "blue clip", "desc", 1002L,
                ownedObjectPath(token), null, 30, RECORDED_DATE, true);
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blueVideo)))
                .andExpect(status().isCreated());
        var redVideo = new CreateVideoRequest(1L, "red clip", "desc", 1003L,
                ownedObjectPath(token), null, 30, RECORDED_DATE, true);
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redVideo)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/gyms/1/videos").param("gymGradeId", "1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].gymGrade.id").value(1002))
                .andExpect(jsonPath("$.data.content[0].gymGrade.label").value("파랑"));
    }

    @Test
    @DisplayName("암장 영상 목록 — 인증 사용자는 자기 비공개 영상도 본다")
    void getGymVideos_includesOwnPrivateVideo() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long ownPrivate = createVideo(owner, false);
        createVideo(other, false);

        var page = dataOf(mockMvc.perform(get("/api/gyms/1/videos")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1)));
        org.assertj.core.api.Assertions.assertThat(page.path("content").get(0).path("id").asLong())
                .isEqualTo(ownPrivate);
    }

    @Test
    @DisplayName("영상 공유 — 공개 영상은 인증 사용자 누구나 share URL을 받는다 (F-02-08)")
    void shareVideo_publicVideo_success() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, true);

        mockMvc.perform(post("/api/videos/" + videoId + "/share")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareUrl").value(org.hamcrest.Matchers.endsWith("/videos/" + videoId)));
    }

    @Test
    @DisplayName("영상 공유 — 비공개 영상은 소유자만 share URL 발급, 타인은 403 V006")
    void shareVideo_privateVideo_accessControl() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long videoId = createVideo(owner, false);

        mockMvc.perform(post("/api/videos/" + videoId + "/share")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareUrl").exists());
        mockMvc.perform(post("/api/videos/" + videoId + "/share")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("V006"));
    }

    @Test
    @DisplayName("영상 공유 — 토큰 없이 호출하면 401")
    void shareVideo_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/videos/1/share"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("영상 공유 — 없는 영상은 404 V001")
    void shareVideo_nonexistentVideo_returns404() throws Exception {
        String token = register("a@hola.com", "climberone");
        mockMvc.perform(post("/api/videos/999999/share")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("V001"));
    }

    // ===== helpers =====

    private CreateVideoRequest videoRequest(boolean isPublic) throws Exception {
        // upload-url 발급(자기 소유 prefix)을 거친 objectPath를 사용해야 createVideo가 통과한다.
        return new CreateVideoRequest(1L, "My Send", "a clean ascent", 1003L,
                "REPLACE", null, 45, RECORDED_DATE, isPublic);
    }

    /** 자기 소유 prefix(videos/uploads/{userId}/)에 해당하는 objectPath를 upload-url API로 발급받는다. */
    private String ownedObjectPath(String token) throws Exception {
        return dataOf(mockMvc.perform(post("/api/videos/upload-url")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UploadUrlRequest("test-" + java.util.UUID.randomUUID() + ".mp4",
                                10_000_000L, "video/mp4"))))
                .andExpect(status().isOk()))
                .path("objectPath").asText();
    }

    /** 자기 소유 prefix(videos/thumbnails/{userId}/)에 해당하는 thumbnailPath를 multipart API로 업로드한다. */
    private String uploadedThumbnailPath(String token) throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "thumbnail-" + java.util.UUID.randomUUID() + ".jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-jpeg-thumbnail".getBytes(StandardCharsets.UTF_8));
        return dataOf(mockMvc.perform(multipart("/api/videos/thumbnail")
                .file(image)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()))
                .path("thumbnailPath").asText();
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());
        return dataOf(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD)))))
                .path("accessToken").asText();
    }

    private void withdraw(String token) throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest(PASSWORD, null))))
                .andExpect(status().isOk());
    }

    private long createVideo(String token, boolean isPublic) throws Exception {
        return createVideoOn(token, isPublic, RECORDED_DATE);
    }

    private long createVideoOn(String token, boolean isPublic, LocalDate recordedDate) throws Exception {
        return createVideoAtGym(token, isPublic, 1L, 1003L, recordedDate);
    }

    private long createVideoAtGym(String token, boolean isPublic, Long gymId, Long gymGradeId) throws Exception {
        return createVideoAtGym(token, isPublic, gymId, gymGradeId, RECORDED_DATE);
    }

    private long createVideoAtGym(String token, boolean isPublic, Long gymId, Long gymGradeId,
                                  LocalDate recordedDate) throws Exception {
        var req = new CreateVideoRequest(gymId, "My Send", "a clean ascent", gymGradeId,
                ownedObjectPath(token), null, 45, recordedDate, isPublic);
        return dataOf(mockMvc.perform(post("/api/videos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    private void insertAnalysis(long videoId, List<String> techniques, Boolean isDynamic) throws Exception {
        String techniquesJson = objectMapper.writeValueAsString(techniques);
        jdbcTemplate.update("""
                INSERT INTO analysis_video_results (
                    video_id, model_version, ai_techniques, ai_is_dynamic, final_techniques, final_is_dynamic
                )
                VALUES (?, 'test', CAST(? AS jsonb), ?, CAST(? AS jsonb), ?)
                """, videoId, techniquesJson, isDynamic, techniquesJson, isDynamic);
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
