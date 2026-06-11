package com.holaclimbing.server.domain.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
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
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recommendation 도메인 통합 테스트 — 홈 피드(팔로잉 + 추천 혼합).
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/videos-schema.sql",
        "classpath:sql/notifications-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RecommendationIntegrationTest {

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
    @DisplayName("홈 피드 — 팔로잉 영상이 먼저, 나머지는 추천으로 노출된다")
    void getVideoFeed_followingFirst() throws Exception {
        String viewer = register("viewer@hola.com", "viewer");
        String followed = register("followed@hola.com", "followeduser");
        String stranger = register("stranger@hola.com", "stranger");
        long followedId = userMapper.findByEmail("followed@hola.com").getId();

        mockMvc.perform(post("/api/users/" + followedId + "/follow")
                .header("Authorization", "Bearer " + viewer)).andExpect(status().isOk());

        createVideo(stranger);
        createVideo(followed);

        mockMvc.perform(get("/api/recommendations/videos").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].source").value("following"))
                .andExpect(jsonPath("$.data.content[0].gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.content[0].gymGrade.id").value(1002))
                .andExpect(jsonPath("$.data.content[0].gymGrade.label").value("파랑"))
                .andExpect(jsonPath("$.data.content[0].grade").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].source").value("recommended"))
                .andExpect(jsonPath("$.data.content[1].gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.content[1].gymGrade.id").value(1002))
                .andExpect(jsonPath("$.data.content[1].grade").doesNotExist());
    }

    @Test
    @DisplayName("홈 피드 — 본인 영상은 추천 피드에서 제외된다")
    void getVideoFeed_excludesOwnVideos() throws Exception {
        String viewer = register("viewer@hola.com", "viewer");
        createVideo(viewer);

        mockMvc.perform(get("/api/recommendations/videos").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist());
    }

    @Test
    @DisplayName("홈 피드 — 차단한 업로더의 공개 영상은 제외된다")
    void getVideoFeed_excludesBlockedUploaderVideos() throws Exception {
        String viewer = register("viewer@hola.com", "viewer");
        String blocked = register("blocked@hola.com", "blockeduser");
        long blockedId = userMapper.findByEmail("blocked@hola.com").getId();

        createVideo(blocked);
        mockMvc.perform(post("/api/users/" + blockedId + "/block")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/recommendations/videos").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist());
    }

    @Test
    @DisplayName("홈 피드 — 나를 차단한 업로더의 공개 영상도 제외된다")
    void getVideoFeed_excludesUploaderWhoBlockedViewer() throws Exception {
        String viewer = register("viewer-blocked@hola.com", "viewer");
        String blocker = register("blocker@hola.com", "blockeruser");
        long viewerId = userMapper.findByEmail("viewer-blocked@hola.com").getId();

        createVideo(blocker);
        mockMvc.perform(post("/api/users/" + viewerId + "/block")
                        .header("Authorization", "Bearer " + blocker))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/recommendations/videos").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist());
    }

    @Test
    @DisplayName("홈 피드 — 임베딩이 있으면 가까운 암장 영상이 먼저 노출된다")
    void getVideoFeed_whenEmbeddingsExist_ordersByVectorSimilarity() throws Exception {
        String viewer = register("viewer-vector@hola.com", "viewer");
        String nearUploader = register("near@hola.com", "nearuser");
        String farUploader = register("far@hola.com", "faruser");
        setUserEmbedding("viewer-vector@hola.com", 2);

        createVideoAtGym(nearUploader, 2L, 1005L, "near clip");
        createVideoAtGym(farUploader, 1L, 1002L, "far clip");

        mockMvc.perform(get("/api/recommendations/videos").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].source").value("recommended"))
                .andExpect(jsonPath("$.data.content[0].gymName").value("ClimbingPark Hongdae"))
                .andExpect(jsonPath("$.data.content[0].gymGrade.id").value(1005))
                .andExpect(jsonPath("$.data.content[1].gymName").value("TheClimb Gangnam"));
    }

    @Test
    @DisplayName("홈 피드 — 팔로잉 영상은 유사도 점수에서 보너스를 받는다")
    void getVideoFeed_whenFollowingVideoIsClose_appliesFollowingBoost() throws Exception {
        String viewer = register("viewer-boost@hola.com", "viewer");
        String followed = register("followed-boost@hola.com", "followeduser");
        String exactMatch = register("exact-boost@hola.com", "exactuser");
        long followedId = userMapper.findByEmail("followed-boost@hola.com").getId();

        mockMvc.perform(post("/api/users/" + followedId + "/follow")
                .header("Authorization", "Bearer " + viewer)).andExpect(status().isOk());
        setUserEmbedding("viewer-boost@hola.com", 2);
        setGymEmbedding(1L, vectorWithFirstTwoDimensions(0.6, 0.8));

        createVideoAtGym(exactMatch, 2L, 1005L, "exact non-following");
        createVideoAtGym(followed, 1L, 1002L, "boosted following");

        mockMvc.perform(get("/api/recommendations/videos").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].source").value("following"))
                .andExpect(jsonPath("$.data.content[0].gymName").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.content[1].source").value("recommended"))
                .andExpect(jsonPath("$.data.content[1].gymName").value("ClimbingPark Hongdae"));
    }

    @Test
    @DisplayName("홈 피드 — 커서 기반으로 다음 페이지를 조회한다")
    void getVideoFeed_cursorPagination() throws Exception {
        String viewer = register("viewer-cursor@hola.com", "viewer");
        String firstUploader = register("cursor-first@hola.com", "cursorfirst");
        String secondUploader = register("cursor-second@hola.com", "cursorsecond");
        String thirdUploader = register("cursor-third@hola.com", "cursorthird");

        createVideo(firstUploader);
        createVideo(secondUploader);
        createVideo(thirdUploader);

        JsonNode firstPage = dataOf(mockMvc.perform(get("/api/recommendations/videos")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").isString())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist())
                .andExpect(jsonPath("$.data.totalPages").doesNotExist())
                .andExpect(jsonPath("$.data.page").doesNotExist()));

        String cursor = firstPage.path("nextCursor").asText();

        mockMvc.perform(get("/api/recommendations/videos")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("feed clip"))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalElements").doesNotExist());
    }

    @Test
    @DisplayName("홈 피드 실패 — 잘못된 커서는 400")
    void getVideoFeed_invalidCursor_returns400() throws Exception {
        String viewer = register("viewer-invalid-cursor@hola.com", "viewer");

        mockMvc.perform(get("/api/recommendations/videos")
                        .param("cursor", "!!!not-base64!!!")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());

        String missingPayloadCursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/recommendations/videos")
                        .param("cursor", missingPayloadCursor)
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("홈 피드 실패 — 토큰 없이 호출하면 401")
    void getVideoFeed_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/recommendations/videos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("암장 추천 — 내 주변 암장을 스타일 유사도 순으로 반환한다")
    void getNearbyGyms_whenEmbeddingsExist_ordersByStyleSimilarity() throws Exception {
        String viewer = register("viewer-gym-rec@hola.com", "viewer");
        setUserEmbedding("viewer-gym-rec@hola.com", 2);

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "127.0200")
                        .param("radius", "12")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].name").value("ClimbingPark Hongdae"))
                .andExpect(jsonPath("$.data[0].rankingDistance").isNumber())
                .andExpect(jsonPath("$.data[0].distanceKm").isNumber())
                .andExpect(jsonPath("$.data[0].source").value("style_match"))
                .andExpect(jsonPath("$.data[1].id").value(1))
                .andExpect(jsonPath("$.data[1].source").value("style_match"));
    }

    @Test
    @DisplayName("암장 추천 — 사용자 임베딩이 없으면 거리순 fallback")
    void getNearbyGyms_withoutUserEmbedding_ordersByDistance() throws Exception {
        String viewer = register("viewer-gym-nearby@hola.com", "viewer");

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "127.0200")
                        .param("radius", "12")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].rankingDistance").doesNotExist())
                .andExpect(jsonPath("$.data[0].source").value("nearby"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].source").value("nearby"));
    }

    @Test
    @DisplayName("암장 추천 — 반경 밖 암장은 제외된다")
    void getNearbyGyms_excludesGymsOutsideRadius() throws Exception {
        String viewer = register("viewer-gym-radius@hola.com", "viewer");

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "127.0200")
                        .param("radius", "0.1")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("암장 추천 실패 — 좌표 범위를 벗어나면 400")
    void getNearbyGyms_invalidCoordinates_returns400() throws Exception {
        String viewer = register("viewer-gym-invalid@hola.com", "viewer");

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "91")
                        .param("lng", "127.0200")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "181")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "NaN")
                        .param("lng", "127.0200")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "Infinity")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "127.0200")
                        .param("radius", "NaN")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "127.0200")
                        .param("radius", "Infinity")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("암장 추천 실패 — 토큰 없이 호출하면 401")
    void getNearbyGyms_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/recommendations/gyms")
                        .param("lat", "37.5000")
                        .param("lng", "127.0200"))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private void createVideo(String token) throws Exception {
        createVideoAtGym(token, 1L, 1002L, "feed clip");
    }

    private void createVideoAtGym(String token, Long gymId, Long gymGradeId, String title) throws Exception {
        long userId = dataOf(mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())).path("userId").asLong();
        String path = "videos/uploads/" + userId + "/test-" + java.util.UUID.randomUUID() + ".mp4";
        var request = new CreateVideoRequest(gymId, title, "desc", gymGradeId,
                path, null, 30, java.time.LocalDate.of(2026, 6, 3), true);
        mockMvc.perform(post("/api/videos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void setUserEmbedding(String email, int hotDimension) {
        jdbcTemplate.update("UPDATE users SET style_embedding = ?::vector WHERE email = ?",
                oneHotVector(hotDimension), email);
    }

    private void setGymEmbedding(Long gymId, String vector) {
        jdbcTemplate.update("UPDATE gyms SET style_embedding = ?::vector WHERE id = ?", vector, gymId);
    }

    private String oneHotVector(int hotDimension) {
        if (hotDimension < 1 || hotDimension > 64) {
            throw new IllegalArgumentException("hotDimension must be between 1 and 64");
        }
        StringBuilder vector = new StringBuilder("[");
        for (int dimension = 1; dimension <= 64; dimension++) {
            if (dimension > 1) {
                vector.append(',');
            }
            vector.append(dimension == hotDimension ? '1' : '0');
        }
        return vector.append(']').toString();
    }

    private String vectorWithFirstTwoDimensions(double first, double second) {
        StringBuilder vector = new StringBuilder("[");
        for (int dimension = 1; dimension <= 64; dimension++) {
            if (dimension > 1) {
                vector.append(',');
            }
            if (dimension == 1) {
                vector.append(first);
            } else if (dimension == 2) {
                vector.append(second);
            } else {
                vector.append('0');
            }
        }
        return vector.append(']').toString();
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
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
