package com.holaclimbing.server.domain.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import static com.holaclimbing.server.TestSignupRequests.signupRequest;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.dto.request.UpdateProfileRequest;
import com.holaclimbing.server.domain.user.dto.request.WithdrawRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User 프로필/팔로우/차단 통합 테스트.
 * 회원가입→인증→로그인으로 발급한 JWT로 보호 엔드포인트를 호출하여,
 * JWT 필터→SecurityContext→컨트롤러로 이어지는 인증 경로까지 함께 검증한다.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/terms-data.sql",
        "classpath:sql/notifications-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserProfileIntegrationTest {

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
    @DisplayName("내 프로필 조회 실패 — 토큰 없이 호출하면 401")
    void getMyProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내 프로필 조회 성공 — 토큰으로 본인 정보를 반환한다")
    void getMyProfile_withToken_success() throws Exception {
        TestUser me = register("me@hola.com", "climberone");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + me.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(me.id()))
                .andExpect(jsonPath("$.data.email").value("me@hola.com"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.followerCount").value(0))
                .andExpect(jsonPath("$.data.followingCount").value(0))
                .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    @Test
    @DisplayName("내 프로필 수정 성공 — 닉네임/소개가 갱신된다")
    void updateMyProfile_success() throws Exception {
        TestUser me = register("me@hola.com", "climberone");

        var body = objectMapper.writeValueAsString(
                new UpdateProfileRequest("newnickname", null, "bouldering for 6 months"));
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("newnickname"))
                .andExpect(jsonPath("$.data.bio").value("bouldering for 6 months"));

        assertThat(userMapper.findById(me.id()).getNickname()).isEqualTo("newnickname");
    }

    @Test
    @DisplayName("프로필 이미지 등록 — multipart 이미지를 GCS에 업로드하고 읽기 URL을 반환한다")
    void uploadProfileImage_success() throws Exception {
        TestUser me = register("image@hola.com", "imageuser");
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-jpeg-profile-image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(image)
                        .header("Authorization", "Bearer " + me.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImage").isString())
                .andExpect(jsonPath("$.data.profileImage").value(org.hamcrest.Matchers.containsString(
                        "profile-images/" + me.id() + "/")))
                .andExpect(jsonPath("$.data.profileImage").value(org.hamcrest.Matchers.containsString(
                        "X-Goog-Signature=")));

        String stored = jdbcTemplate.queryForObject(
                "SELECT profile_image FROM users WHERE id = ?",
                String.class,
                me.id());
        assertThat(stored)
                .startsWith("profile-images/" + me.id() + "/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("프로필 이미지 등록 실패 — 지원하지 않는 이미지 형식은 400")
    void uploadProfileImage_unsupportedFormat_returns400() throws Exception {
        TestUser me = register("gif@hola.com", "gifuser");
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "profile.gif",
                MediaType.IMAGE_GIF_VALUE,
                "fake-gif-profile-image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(image)
                        .header("Authorization", "Bearer " + me.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("내 프로필 수정 실패 — 다른 사용자의 닉네임으로 변경 시 409 U008")
    void updateMyProfile_duplicateNickname_returns409() throws Exception {
        register("a@hola.com", "climberone");
        TestUser b = register("b@hola.com", "climbertwo");

        var body = objectMapper.writeValueAsString(new UpdateProfileRequest("climberone", null, null));
        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + b.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("U008"));
    }

    @Test
    @DisplayName("다른 사용자 프로필 조회 성공 — 비로그인 시 is_following=false")
    void getUserProfile_success() throws Exception {
        TestUser target = register("target@hola.com", "climberone");

        mockMvc.perform(get("/api/users/" + target.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(target.id()))
                .andExpect(jsonPath("$.data.isFollowing").value(false));
    }

    @Test
    @DisplayName("다른 사용자 프로필 조회 실패 — 없는 사용자는 404 U001")
    void getUserProfile_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("다른 사용자 프로필 조회 실패 — 정지된 사용자는 공개 프로필에서 숨긴다")
    void getUserProfile_suspendedUser_returns404() throws Exception {
        TestUser target = register("suspended-profile@hola.com", "suspendedprofile");
        userMapper.updateStatus(target.id(), "SUSPENDED");

        mockMvc.perform(get("/api/users/" + target.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("팔로우 성공 — 팔로우 후 프로필에 is_following=true, 팔로워 수가 증가한다")
    void follow_thenProfileShowsFollowing() throws Exception {
        TestUser a = register("a@hola.com", "climberone");
        TestUser b = register("b@hola.com", "climbertwo");

        mockMvc.perform(post("/api/users/" + b.id() + "/follow")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + b.id()).header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFollowing").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + a.token()))
                .andExpect(jsonPath("$.data.followingCount").value(1));
    }

    @Test
    @DisplayName("팔로우 실패 — 자기 자신을 팔로우하면 400")
    void follow_self_returns400() throws Exception {
        TestUser a = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/users/" + a.id() + "/follow")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("팔로우 실패 — 이미 팔로우한 사용자는 400")
    void follow_alreadyFollowing_returns400() throws Exception {
        TestUser a = register("a@hola.com", "climberone");
        TestUser b = register("b@hola.com", "climbertwo");

        mockMvc.perform(post("/api/users/" + b.id() + "/follow")
                .header("Authorization", "Bearer " + a.token())).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + b.id() + "/follow")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("언팔로우 성공 — 언팔로우 후 팔로워 수가 0으로 돌아온다")
    void unfollow_success() throws Exception {
        TestUser a = register("a@hola.com", "climberone");
        TestUser b = register("b@hola.com", "climbertwo");

        mockMvc.perform(post("/api/users/" + b.id() + "/follow")
                .header("Authorization", "Bearer " + a.token())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + b.id() + "/follow")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + b.id()))
                .andExpect(jsonPath("$.data.followerCount").value(0));
    }

    @Test
    @DisplayName("팔로워/팔로잉 목록 — 팔로우 관계가 양쪽 목록에 반영된다")
    void followerAndFollowingLists() throws Exception {
        TestUser a = register("a@hola.com", "climberone");
        TestUser b = register("b@hola.com", "climbertwo");

        mockMvc.perform(post("/api/users/" + b.id() + "/follow")
                .header("Authorization", "Bearer " + a.token())).andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + b.id() + "/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(a.id()));

        mockMvc.perform(get("/api/users/" + a.id() + "/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(b.id()));
    }

    @Test
    @DisplayName("팔로워/팔로잉 목록 — 정지된 사용자는 카운트와 목록에서 제외된다")
    void followerAndFollowingLists_excludeSuspendedUsers() throws Exception {
        TestUser active = register("active-profile@hola.com", "activeprofile");
        TestUser suspendedFollower = register("suspended-follower@hola.com", "suspendedfollower");
        TestUser suspendedFollowing = register("suspended-following@hola.com", "suspendedfollowing");

        mockMvc.perform(post("/api/users/" + active.id() + "/follow")
                .header("Authorization", "Bearer " + suspendedFollower.token())).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + suspendedFollowing.id() + "/follow")
                .header("Authorization", "Bearer " + active.token())).andExpect(status().isOk());

        userMapper.updateStatus(suspendedFollower.id(), "SUSPENDED");
        userMapper.updateStatus(suspendedFollowing.id(), "SUSPENDED");

        mockMvc.perform(get("/api/users/" + active.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(0))
                .andExpect(jsonPath("$.data.followingCount").value(0));
        mockMvc.perform(get("/api/users/" + active.id() + "/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mockMvc.perform(get("/api/users/" + active.id() + "/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("차단 — 차단 시 양방향 팔로우가 해제되고 차단 목록에 추가된다")
    void block_removesFollowAndAppearsInBlockList() throws Exception {
        TestUser a = register("a@hola.com", "climberone");
        TestUser b = register("b@hola.com", "climbertwo");

        mockMvc.perform(post("/api/users/" + b.id() + "/follow")
                .header("Authorization", "Bearer " + a.token())).andExpect(status().isOk());

        mockMvc.perform(post("/api/users/" + b.id() + "/block")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + b.id()))
                .andExpect(jsonPath("$.data.followerCount").value(0));
        mockMvc.perform(get("/api/users/me/blocks").header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].userId").value(b.id()));
    }

    @Test
    @DisplayName("차단 해제 — 차단 목록에서 제거된다")
    void unblock_success() throws Exception {
        TestUser a = register("a@hola.com", "climberone");
        TestUser b = register("b@hola.com", "climbertwo");

        mockMvc.perform(post("/api/users/" + b.id() + "/block")
                .header("Authorization", "Bearer " + a.token())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + b.id() + "/block")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me/blocks").header("Authorization", "Bearer " + a.token()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("회원 탈퇴 — 비밀번호 확인 후 탈퇴하면 재로그인이 막힌다")
    void withdraw_success() throws Exception {
        TestUser me = register("quit@hola.com", "quitter");

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest(PASSWORD, "이제 안 써요"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("quit@hola.com", PASSWORD))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("회원 탈퇴 — 탈퇴 row status를 DELETED로 남긴다")
    void withdraw_marksStatusDeleted() throws Exception {
        TestUser me = register("quit@hola.com", "quitter");

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest(PASSWORD, null))))
                .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM users WHERE id = ?",
                String.class,
                me.id());
        assertThat(status).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("회원 탈퇴 실패 — 비밀번호가 틀리면 401")
    void withdraw_wrongPassword_returns401() throws Exception {
        TestUser me = register("quit@hola.com", "quitter");

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + me.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WithdrawRequest("wrongpassword", null))))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private record TestUser(Long id, String token) {
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료한 사용자를 만들고 (id, accessToken)을 반환. */
    private TestUser register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());

        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());

        String token = dataOf(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD)))))
                .path("accessToken").asText();
        return new TestUser(user.getId(), token);
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
