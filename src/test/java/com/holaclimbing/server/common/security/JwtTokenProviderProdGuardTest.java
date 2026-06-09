package com.holaclimbing.server.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderProdGuardTest {

    private static final String DEFAULT_DEVELOPMENT_SECRET =
            "9f2b7a8c4e6d1f0a3b5c7d9e2f4a6b8c1d3e5f7a9b0c2d4e6f8a1b3c5d7e9f0a";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtGuardConfig.class)
            .withPropertyValues(
                    "jwt.access-token-validity-minutes=30",
                    "jwt.refresh-token-validity-days=14",
                    "jwt.issuer=hola-climbing",
                    "app.mail.mode=smtp");

    @Test
    @DisplayName("prod 프로필에서는 개발용 JWT 기본 시크릿으로 기동할 수 없다")
    void prodProfile_withDefaultJwtSecret_failsContext() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=" + DEFAULT_DEVELOPMENT_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("JWT_SECRET");
                });
    }

    @Test
    @DisplayName("prod 프로필에서도 명시적 JWT 시크릿이면 기동할 수 있다")
    void prodProfile_withExplicitJwtSecret_starts() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "jwt.secret=prod-secret-prod-secret-prod-secret-123456")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(JwtTokenProvider.class)
    static class JwtGuardConfig {
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
