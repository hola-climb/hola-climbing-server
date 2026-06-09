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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminGymIntegrationTest {

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
    @DisplayName("관리자 암장 - pending 목록 조회")
    void searchGyms_pending_returnsRows() throws Exception {
        String adminToken = registerAndLoginAdmin();
        insertPendingGym();

        mockMvc.perform(get("/api/admin/gyms")
                        .param("status", "pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Pending Gym"))
                .andExpect(jsonPath("$.data.content[0].status").value("pending"));
    }

    @Test
    @DisplayName("관리자 암장 - 제안 승인")
    void approveGym_changesStatusToActive() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long pendingGymId = insertPendingGym();

        mockMvc.perform(post("/api/admin/gyms/" + pendingGymId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"정보 확인 완료\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));

        mockMvc.perform(get("/api/gyms/" + pendingGymId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자 암장 - 난이도 목록 교체")
    void replaceGrades_replacesActiveGrades() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long gymId = insertActiveGymWithGrade();

        mockMvc.perform(put("/api/admin/gyms/" + gymId + "/grades")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grades":[
                                  {"label":"노랑","difficultyOrder":10},
                                  {"label":"파랑","difficultyOrder":20}
                                ],"reason":"운영 난이도 갱신"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].label").value("노랑"));
    }

    @Test
    @DisplayName("관리자 암장 - 난이도 목록은 한 번에 100개까지만 교체할 수 있다")
    void replaceGrades_tooManyGrades_returns400() throws Exception {
        String adminToken = registerAndLoginAdmin();
        long gymId = insertActiveGymWithGrade();

        mockMvc.perform(put("/api/admin/gyms/" + gymId + "/grades")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[" + repeatedGrades(101) + "],\"reason\":\"too many\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("관리자 암장 일괄입력 - preview는 저장하지 않고 오류 행을 반환한다")
    void previewImport_returnsInvalidRowsWithoutSaving() throws Exception {
        String adminToken = registerAndLoginAdmin();

        mockMvc.perform(post("/api/admin/gyms/import/preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[
                                  {"externalKey":"ok-1","name":"정상 암장","address":"서울","lat":37.1,"lng":127.1,
                                   "regionCode":"seoul","grades":[{"label":"노랑","difficultyOrder":10}]},
                                  {"externalKey":"bad-1","name":"","address":"서울","grades":[]}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validCount").value(1))
                .andExpect(jsonPath("$.data.invalidRows[0].externalKey").value("bad-1"));

        Long savedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gyms WHERE name = '정상 암장'", Long.class);
        org.assertj.core.api.Assertions.assertThat(savedCount).isZero();
    }

    @Test
    @DisplayName("관리자 암장 일괄입력 - 한 번에 500행까지만 받을 수 있다")
    void previewImport_tooManyRows_returns400() throws Exception {
        String adminToken = registerAndLoginAdmin();

        mockMvc.perform(post("/api/admin/gyms/import/preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[" + repeatedImportRows(501) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("관리자 암장 일괄입력 - 유효한 행을 저장한다")
    void applyImport_savesValidRows() throws Exception {
        String adminToken = registerAndLoginAdmin();

        mockMvc.perform(post("/api/admin/gyms/import")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[
                                  {"externalKey":"import-1","name":"Imported Gym","address":"서울","lat":37.1,"lng":127.1,
                                   "regionCode":"seoul","grades":[{"label":"노랑","difficultyOrder":10}]}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importedCount").value(1));

        mockMvc.perform(get("/api/admin/gyms")
                        .param("keyword", "Imported")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Imported Gym"));
    }

    private long insertPendingGym() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO gyms (name, address, region_code, status, created_by)
                VALUES ('Pending Gym', 'Seoul', 'seoul', 'pending', 1)
                RETURNING id
                """, Long.class);
    }

    private long insertActiveGymWithGrade() {
        Long gymId = jdbcTemplate.queryForObject("""
                INSERT INTO gyms (name, address, region_code, status, created_by)
                VALUES ('Active Gym', 'Seoul', 'seoul', 'active', 1)
                RETURNING id
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO gym_grades (gym_id, label, difficulty_order)
                VALUES (?, '기존', 10)
                """, gymId);
        return gymId;
    }

    private String repeatedGrades(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> "{\"label\":\"G" + i + "\",\"difficultyOrder\":" + i + "}")
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String repeatedImportRows(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> "{\"externalKey\":\"import-" + i
                        + "\",\"name\":\"Gym " + i
                        + "\",\"address\":\"서울\",\"lat\":37.1,\"lng\":127.1,"
                        + "\"regionCode\":\"seoul\",\"grades\":[{\"label\":\"노랑\",\"difficultyOrder\":10}]}")
                .collect(java.util.stream.Collectors.joining(","));
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
