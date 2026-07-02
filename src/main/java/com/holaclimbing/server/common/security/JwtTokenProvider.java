package com.holaclimbing.server.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Access/Refresh 토큰 발급 + 파싱 + 검증 유틸.
 * - subject: userId (Long → String)
 * - claim "type": "access" | "refresh"
 * - claim "email", "role": Access 토큰에만
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String DEVELOPMENT_SECRET =
            "9f2b7a8c4e6d1f0a3b5c7d9e2f4a6b8c1d3e5f7a9b0c2d4e6f8a1b3c5d7e9f0a";
    private static final int HS256_MIN_SECRET_BYTES = 32;

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ISSUED_AT_MILLIS = "iat_ms";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;
    private final Environment environment;
    private SecretKey key;

    @PostConstruct
    void init() {
        validateSecret();
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    private void validateSecret() {
        String secret = props.secret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("JWT_SECRET must be configured.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < HS256_MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256.");
        }
        if (environment.matchesProfiles("prod") && DEVELOPMENT_SECRET.equals(secret)) {
            throw new IllegalStateException("JWT_SECRET must not use the development default in prod profile.");
        }
    }

    public String createAccessToken(Long userId, String email, String role) {
        return createToken(userId, email, role, TYPE_ACCESS,
                Duration.ofMinutes(props.accessTokenValidityMinutes()));
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, null, null, TYPE_REFRESH,
                Duration.ofDays(props.refreshTokenValidityDays()));
    }

    private String createToken(Long userId, String email, String role, String type, Duration validity) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validity.toMillis());

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuer(props.issuer())
                .issuedAt(now)
                .expiration(expiry)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_ISSUED_AT_MILLIS, now.getTime());

        if (email != null) {
            builder.claim(CLAIM_EMAIL, email);
        }
        if (role != null) {
            builder.claim(CLAIM_ROLE, role);
        }

        return builder.signWith(key, Jwts.SIG.HS256).compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getIssuedAtMillis(Claims claims) {
        Object value = claims.get(CLAIM_ISSUED_AT_MILLIS);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // Fall back to the standard iat claim below.
            }
        }
        return claims.getIssuedAt().toInstant().toEpochMilli();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /** 토큰 식별자(jti). 블랙리스트 등록·조회에 사용. */
    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
    }
}
