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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = "classpath:sql/users-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminUserIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("관리자 회원 - 목록 검색")
    void searchUsers_returnsUsers() throws Exception {
        String adminToken = registerAndLoginAdmin();
        registerAndLoginUser("climber@hola.com", "targetclimber");

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", "climber")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].nickname").value("targetclimber"));
    }

    @Test
    @DisplayName("관리자 회원 - 단건 상세 조회")
    void getUser_returnsDetail() throws Exception {
        String adminToken = registerAndLoginAdmin();
        registerAndLoginUser("detail@hola.com", "detailuser");
        long userId = userMapper.findByEmail("detail@hola.com").getId();

        mockMvc.perform(get("/api/admin/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("detail@hola.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("관리자 회원 - 정지하면 기존 토큰이 무효화된다")
    void suspendUser_revokesExistingToken() throws Exception {
        String adminToken = registerAndLoginAdmin();
        String userToken = registerAndLoginUser("u@hola.com", "targetuser");
        long userId = userMapper.findByEmail("u@hola.com").getId();

        Thread.sleep(1100);
        mockMvc.perform(patch("/api/admin/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"신고 누적\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 회원 - 토큰 강제 폐기")
    void revokeTokens_revokesExistingToken() throws Exception {
        String adminToken = registerAndLoginAdmin();
        String userToken = registerAndLoginUser("revoke@hola.com", "revokeuser");
        long userId = userMapper.findByEmail("revoke@hola.com").getId();

        Thread.sleep(1100);
        mockMvc.perform(post("/api/admin/users/" + userId + "/revoke-tokens")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"계정 보안 조치\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 회원 - 일반 회원을 관리자로 승격하면 재로그인 후 관리자 API를 사용할 수 있다")
    void changeRole_promotesUserToAdmin_andReloginAccessesAdminApi() throws Exception {
        String adminToken = registerAndLoginAdmin();
        registerAndLoginUser("promote@hola.com", "promoteuser");
        long userId = userMapper.findByEmail("promote@hola.com").getId();

        mockMvc.perform(patch("/api/admin/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"운영자 지정\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        String promotedToken = login("promote@hola.com");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + promotedToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자 회원 - 관리자를 일반 회원으로 강등하면 기존 관리자 토큰이 무효화된다")
    void changeRole_demotesAdminAndRevokesExistingToken() throws Exception {
        String adminToken = registerAndLoginAdmin();
        registerAndLoginUser("demote@hola.com", "demoteuser");
        long userId = userMapper.findByEmail("demote@hola.com").getId();

        mockMvc.perform(patch("/api/admin/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"보조 운영자 지정\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        String targetAdminToken = login("demote@hola.com");

        Thread.sleep(1100);
        mockMvc.perform(patch("/api/admin/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"운영자 권한 회수\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + targetAdminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 회원 - 마지막 활성 관리자는 강등할 수 없다")
    void changeRole_lastAdmin_returns400() throws Exception {
        String staleAdminToken = registerAndLoginAdmin("caller@hola.com", "calleradmin");
        long callerId = userMapper.findByEmail("caller@hola.com").getId();
        registerAndLoginUser("lastadmin@hola.com", "lastadmin");
        long lastAdminId = userMapper.findByEmail("lastadmin@hola.com").getId();
        userMapper.updateRole(lastAdminId, "ADMIN");
        userMapper.updateRole(callerId, "USER");

        mockMvc.perform(patch("/api/admin/users/" + lastAdminId + "/role")
                        .header("Authorization", "Bearer " + staleAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"마지막 관리자 강등 시도\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("관리자 회원 - 자기 자신의 역할은 변경할 수 없다")
    void changeRole_self_returns400() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long adminId = userMapper.findByEmail("admin@hola.com").getId();

        mockMvc.perform(patch("/api/admin/users/" + adminId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"셀프 강등\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("관리자 회원 - 역할 변경 요청 검증")
    void changeRole_invalidRoleOrBlankReason_returns400() throws Exception {
        String adminToken = registerAndLoginAdmin();
        registerAndLoginUser("invalidrole@hola.com", "invalidrole");
        long userId = userMapper.findByEmail("invalidrole@hola.com").getId();

        mockMvc.perform(patch("/api/admin/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\",\"reason\":\"지원하지 않는 역할\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));

        mockMvc.perform(patch("/api/admin/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    private String registerAndLoginAdmin() throws Exception {
        return registerAndLoginAdmin("admin@hola.com", "adminuser");
    }

    private String registerAndLoginAdmin(String email, String nickname) throws Exception {
        registerAndLoginUser(email, nickname);
        var user = userMapper.findByEmail(email);
        userMapper.updateRole(user.getId(), "ADMIN");
        return login(email);
    }

    private String registerAndLoginUser(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
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
