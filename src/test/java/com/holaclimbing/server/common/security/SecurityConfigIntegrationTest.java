package com.holaclimbing.server.common.security;

import com.holaclimbing.server.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.cors.allowed-origins=http://localhost:3000",
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        "metrics.token=test-metrics-token"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Actuator health는 인증 없이 조회할 수 있다")
    void actuatorHealth_isPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator metrics는 인증 없이 조회할 수 없다")
    void actuatorMetrics_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Actuator prometheus는 모니터링 토큰으로 조회할 수 있다")
    void actuatorPrometheus_metricsToken_returnsOk() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer test-metrics-token"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator prometheus는 잘못된 모니터링 토큰으로 조회할 수 없다")
    void actuatorPrometheus_wrongMetricsToken_returns401() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Actuator metrics는 일반 사용자 토큰으로 조회할 수 없다")
    void actuatorMetrics_userToken_returns403() throws Exception {
        String userToken = jwtTokenProvider.createAccessToken(1L, "user@hola.com", "USER");

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Actuator metrics는 관리자 토큰으로 조회할 수 있다")
    void actuatorMetrics_adminToken_returnsOk() throws Exception {
        String adminToken = jwtTokenProvider.createAccessToken(1L, "admin@hola.com", "ADMIN");

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
