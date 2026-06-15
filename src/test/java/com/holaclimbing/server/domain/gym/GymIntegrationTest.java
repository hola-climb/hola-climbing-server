package com.holaclimbing.server.domain.gym;

import com.holaclimbing.server.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gym 도메인 조회 API 통합 테스트.
 * Testcontainers PostgreSQL 위에서 시드 데이터(gyms-data.sql)로 검색·근처·상세를 검증한다.
 * gym 5는 status='closed'라 모든 조회 결과에서 제외되어야 한다.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {"classpath:sql/gyms-schema.sql", "classpath:sql/gyms-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GymIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("암장 검색 — 필터 없으면 활성 암장 4개를 이름순으로 반환한다")
    void searchGyms_noFilter_returnsActiveOnly() throws Exception {
        mockMvc.perform(get("/api/gyms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].id").value(3));
    }

    @Test
    @DisplayName("암장 검색 — 키워드(이름 부분일치)로 필터링한다")
    void searchGyms_byKeyword() throws Exception {
        mockMvc.perform(get("/api/gyms").param("keyword", "TheClimb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[1].id").value(1))
                .andExpect(jsonPath("$.data.content[1].thumbnailUrl").isString())
                .andExpect(jsonPath("$.data.content[1].thumbnailUrl").value(org.hamcrest.Matchers.containsString(
                        "gyms/profile-images/1/seed.jpg")))
                .andExpect(jsonPath("$.data.content[1].thumbnailUrl").value(org.hamcrest.Matchers.containsString(
                        "X-Goog-Signature=")));
    }

    @Test
    @DisplayName("암장 검색 — q 별칭도 키워드와 동일하게 이름 부분일치로 필터링한다")
    void searchGyms_byQueryAlias() throws Exception {
        mockMvc.perform(get("/api/gyms").param("q", "Pangyo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("BoulderProject Pangyo"));
    }

    @Test
    @DisplayName("암장 검색 — LIKE wildcard 문자는 실제 포함 문자로만 검색한다")
    void searchGyms_escapesLikeWildcards() throws Exception {
        mockMvc.perform(get("/api/gyms").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("암장 검색 — 지역 코드로 필터링한다")
    void searchGyms_byRegion() throws Exception {
        mockMvc.perform(get("/api/gyms").param("region", "gyeonggi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("암장 검색 — closed 상태 암장은 결과에서 제외된다")
    void searchGyms_closedExcluded() throws Exception {
        mockMvc.perform(get("/api/gyms").param("region", "busan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("암장 검색 — 페이지 크기로 잘라 반환하고 has_next를 계산한다")
    void searchGyms_pagination() throws Exception {
        mockMvc.perform(get("/api/gyms").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("근처 암장 — 반경 5km 안의 암장만 반환한다")
    void findNearby_smallRadius() throws Exception {
        mockMvc.perform(get("/api/gyms/nearby")
                        .param("lat", "37.4979").param("lng", "127.0276").param("radius", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("근처 암장 — 반경을 넓히면 가까운 순으로 정렬되어 반환된다")
    void findNearby_largeRadius_orderedByDistance() throws Exception {
        mockMvc.perform(get("/api/gyms/nearby")
                        .param("lat", "37.4979").param("lng", "127.0276").param("radius", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[2].id").value(3))
                .andExpect(jsonPath("$.data[3].id").value(4));
    }

    @Test
    @Sql(scripts = {"classpath:sql/gyms-schema.sql", "classpath:sql/gyms-data.sql"},
            statements = "INSERT INTO gyms (id, name, address, lat, lng, region_code, rating_avg, rating_count, status) VALUES (6, 'Floating Edge Gym', 'Antipode Edge', 87.5, 0.0, 'edge', 0.00, 0, 'active')")
    @DisplayName("근처 암장 — 거리 계산 하한 수치 오차가 있어도 500이 나지 않는다")
    void findNearby_antipodalFloatingPointEdge_returnsOk() throws Exception {
        mockMvc.perform(get("/api/gyms/nearby")
                        .param("lat", "-87.5").param("lng", "-180").param("radius", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("근처 암장 — 위도 범위를 벗어난 좌표는 400")
    void findNearby_invalidLatitude_returns400() throws Exception {
        mockMvc.perform(get("/api/gyms/nearby")
                        .param("lat", "100").param("lng", "127.0276"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("암장 상세 — 프로필 이미지 URL을 포함해 반환한다")
    void getGymDetail_success() throws Exception {
        mockMvc.perform(get("/api/gyms/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("TheClimb Gangnam"))
                .andExpect(jsonPath("$.data.thumbnailUrl").isString())
                .andExpect(jsonPath("$.data.thumbnailUrl").value(org.hamcrest.Matchers.containsString(
                        "gyms/profile-images/1/seed.jpg")))
                .andExpect(jsonPath("$.data.thumbnailUrl").value(org.hamcrest.Matchers.containsString(
                        "X-Goog-Signature=")))
                .andExpect(jsonPath("$.data.photos").doesNotExist());
    }

    @Test
    @DisplayName("암장 상세 — closed 상태 암장은 404 G001")
    void getGymDetail_closedGym_returns404() throws Exception {
        mockMvc.perform(get("/api/gyms/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("암장 상세 — 없는 암장은 404 G001")
    void getGymDetail_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/gyms/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("암장 난이도 목록 — 활성 난이도만 난이도순으로 반환한다")
    void getGymGrades_returnsActiveGradesOrdered() throws Exception {
        mockMvc.perform(get("/api/gyms/1/grades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].label").value("초록"))
                .andExpect(jsonPath("$.data[1].label").value("파랑"))
                .andExpect(jsonPath("$.data[2].label").value("빨강"))
                .andExpect(jsonPath("$.data[2].colorHex").doesNotExist());
    }

    @Test
    @DisplayName("암장 난이도 목록 — closed 암장은 404 G001")
    void getGymGrades_closedGym_returns404() throws Exception {
        mockMvc.perform(get("/api/gyms/5/grades"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }
}
