package com.holaclimbing.server.domain.gym;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import static com.holaclimbing.server.TestSignupRequests.signupRequest;
import com.holaclimbing.server.domain.gym.dto.request.CreateReviewRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateReviewRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GymReview 도메인 통합 테스트 — 암장 리뷰 작성·조회·수정·삭제.
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
        "classpath:sql/gym-reviews-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GymReviewIntegrationTest {

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
    @DisplayName("리뷰 작성 — 201, 평점과 내용이 저장된다")
    void createReview_success() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/gyms/1/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "좋은 암장"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.gymId").value(1));
    }

    @Test
    @DisplayName("리뷰 작성 실패 — 같은 암장 중복 리뷰는 409 G003")
    void createReview_duplicate_returns409() throws Exception {
        String token = register("a@hola.com", "climberone");
        var body = objectMapper.writeValueAsString(new CreateReviewRequest(4, "리뷰"));

        mockMvc.perform(post("/api/gyms/1/reviews").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/gyms/1/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("G003"));
    }

    @Test
    @DisplayName("리뷰 작성 실패 — 토큰 없이 호출하면 401")
    void createReview_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/gyms/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "리뷰"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("리뷰 작성 실패 — 없는 암장은 404 G001")
    void createReview_nonexistentGym_returns404() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/gyms/999999/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(5, "리뷰"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("리뷰 목록 — 작성한 리뷰가 목록에 나타난다")
    void getReviews_list() throws Exception {
        String token = register("a@hola.com", "climberone");
        mockMvc.perform(post("/api/gyms/1/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(3, "보통"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/gyms/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].rating").value(3))
                .andExpect(jsonPath("$.data.content[0].nickname").value("climberone"));
    }

    @Test
    @DisplayName("리뷰 목록 실패 — 닫힌 암장은 사용자 API에서 리뷰도 숨긴다")
    void getReviews_closedGym_returns404() throws Exception {
        String token = register("closed-review@hola.com", "closedreviewer");
        mockMvc.perform(post("/api/gyms/1/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(3, "보통"))))
                .andExpect(status().isCreated());

        jdbcTemplate.update("UPDATE gyms SET status = 'closed' WHERE id = ?", 1L);

        mockMvc.perform(get("/api/gyms/1/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("리뷰 목록 — 작성자 닉네임과 프로필 이미지를 함께 반환한다")
    void getReviews_includesAuthorProfile() throws Exception {
        String token = register("profile-review@hola.com", "profilereviewer");
        long userId = userMapper.findByEmail("profile-review@hola.com").getId();
        jdbcTemplate.update("UPDATE users SET profile_image = ? WHERE id = ?",
                "profile-images/" + userId + "/review.jpg", userId);
        mockMvc.perform(post("/api/gyms/1/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(4, "프로필 리뷰"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/gyms/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("프로필 리뷰"))
                .andExpect(jsonPath("$.data.content[0].nickname").value("profilereviewer"))
                .andExpect(jsonPath("$.data.content[0].profileImage").isString())
                .andExpect(jsonPath("$.data.content[0].profileImage").value(org.hamcrest.Matchers.containsString(
                        "profile-images/" + userId + "/review.jpg")))
                .andExpect(jsonPath("$.data.content[0].profileImage").value(org.hamcrest.Matchers.containsString(
                        "X-Goog-Signature=")));
    }

    @Test
    @DisplayName("리뷰 수정·삭제 — 작성자만 가능하며 타인은 403")
    void updateAndDeleteReview_accessControl() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long reviewId = dataOf(mockMvc.perform(post("/api/gyms/1/reviews")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateReviewRequest(2, "별로"))))
                .andExpect(status().isCreated()))
                .path("id").asLong();

        var update = objectMapper.writeValueAsString(new UpdateReviewRequest(5, "다시 보니 좋음"));
        mockMvc.perform(patch("/api/gyms/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/gyms/reviews/" + reviewId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(5));

        mockMvc.perform(delete("/api/gyms/reviews/" + reviewId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/gyms/1/reviews"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ===== helpers =====

    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest(email, PASSWORD, nickname))))
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
