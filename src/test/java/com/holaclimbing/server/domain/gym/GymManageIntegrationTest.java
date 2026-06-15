package com.holaclimbing.server.domain.gym;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import com.holaclimbing.server.domain.gym.dto.request.CreateGymRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateBusinessHoursRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gym 도메인 등록·관리 통합 테스트 — 암장 등록 제안, 운영시간 수정.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GymManageIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("암장 등록 제안 — 201, status=pending으로 등록된다")
    void suggestGym_success() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/gyms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateGymRequest(
                                "새 암장", "서울시 송파구", 37.5, 127.1,
                                "02-000-0000", null, "신규 제안 암장", null, "seoul"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("새 암장"))
                .andExpect(jsonPath("$.data.status").value("pending"));
    }

    @Test
    @DisplayName("암장 등록 제안 실패 — 토큰 없이 호출하면 401")
    void suggestGym_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/gyms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateGymRequest(
                                "새 암장", null, null, null, null, null, null, null, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("레거시 암장 다중 사진 API — 업로드 엔드포인트는 더 이상 제공하지 않는다")
    void legacyUploadPhotoEndpoint_returns404() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/gyms/1/photos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gcsPath":"gyms/1/photo-c.jpg","caption":"새 사진","displayOrder":2}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("레거시 암장 다중 사진 API — 목록 조회 엔드포인트는 더 이상 제공하지 않는다")
    void legacyGetPhotosEndpoint_returns404() throws Exception {
        mockMvc.perform(get("/api/gyms/1/photos"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("암장 상세 — 요일별 운영시간을 포함해 반환한다 (일요일 휴무는 응답에서 제외)")
    void getGymDetail_includesBusinessHours() throws Exception {
        mockMvc.perform(get("/api/gyms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessHours.mon.open").value("06:00"))
                .andExpect(jsonPath("$.data.businessHours.mon.close").value("23:00"))
                .andExpect(jsonPath("$.data.businessHours.sat.open").value("09:00"))
                .andExpect(jsonPath("$.data.businessHours.sun").doesNotExist());
    }

    @Test
    @DisplayName("운영시간 수정 — 200, 등록 제안자 본인이면 전달한 요일별 시각으로 치환된다")
    void updateBusinessHours_success() throws Exception {
        String token = register("a@hola.com", "climberone");
        // created_by가 매칭되어야 권한이 통과하므로 suggestGym으로 본인 소유 암장을 만든다.
        long gymId = suggestGym(token, "My Gym", "addr");
        var body = objectMapper.writeValueAsString(new UpdateBusinessHoursRequest(Map.of(
                "mon", new DayHours("07:00", "22:00"),
                "sat", new DayHours("10:00", "20:00"))));

        mockMvc.perform(patch("/api/gyms/" + gymId + "/business-hours")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessHours.mon.open").value("07:00"))
                .andExpect(jsonPath("$.data.businessHours.sat.close").value("20:00"));
        // GET /api/gyms/{id}는 active 암장만 노출하므로 pending 상태에선 따로 검증하지 않는다.
    }

    @Test
    @DisplayName("운영시간 수정 실패 — 등록 제안자가 아닌 사용자가 호출하면 403")
    void updateBusinessHours_byNonCreator_returns403() throws Exception {
        String owner = register("a@hola.com", "climberone");
        String other = register("b@hola.com", "climbertwo");
        long gymId = suggestGym(owner, "Owner Gym", "addr");
        var body = objectMapper.writeValueAsString(new UpdateBusinessHoursRequest(Map.of(
                "mon", new DayHours("07:00", "22:00"))));

        mockMvc.perform(patch("/api/gyms/" + gymId + "/business-hours")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private long suggestGym(String token, String name, String address) throws Exception {
        return dataOf(mockMvc.perform(post("/api/gyms")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateGymRequest(
                        name, address, 37.5, 127.0, null, null, null, null, "SEOUL"))))
                .andExpect(status().isCreated()))
                .path("id").asLong();
    }

    @Test
    @DisplayName("운영시간 수정 실패 — 토큰 없이 호출하면 401")
    void updateBusinessHours_withoutToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/gyms/1/business-hours")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBusinessHoursRequest(
                                Map.of("mon", new DayHours("07:00", "22:00"))))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("운영시간 수정 실패 — 없는 암장은 404 G001")
    void updateBusinessHours_nonexistentGym_returns404() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(patch("/api/gyms/999999/business-hours")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateBusinessHoursRequest(
                                Map.of("mon", new DayHours("07:00", "22:00"))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    // ===== helpers =====

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
