package com.holaclimbing.server.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 jwt.* 키를 매핑.
 * - secret: HS256 서명 키 (32바이트 이상 필수)
 * - access-token-validity-minutes: Access 토큰 유효기간 (분)
 * - refresh-token-validity-days: Refresh 토큰 유효기간 (일)
 * - issuer: 토큰 발급자
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive int accessTokenValidityMinutes,
        @Positive int refreshTokenValidityDays,
        @NotBlank String issuer
) {
}