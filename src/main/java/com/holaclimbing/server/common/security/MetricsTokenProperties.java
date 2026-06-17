package com.holaclimbing.server.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prometheus가 /actuator/prometheus를 scrape할 때 사용하는 공유 토큰.
 */
@ConfigurationProperties(prefix = "metrics")
public record MetricsTokenProperties(String token) {

    public boolean configured() {
        return token != null && !token.isBlank();
    }
}
