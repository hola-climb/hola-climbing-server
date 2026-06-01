package com.holaclimbing.server.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Access/Refresh 토큰 발급 + 파싱 + 검증 유틸.
 * - subject: userId (Long → String)
 * - claim "type": "access" | "refresh"
 * - claim "email": Access 토큰에만
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EMAIL = "email";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;
    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String email) {
        return createToken(userId, email, TYPE_ACCESS,
                Duration.ofMinutes(props.accessTokenValidityMinutes()));
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, null, TYPE_REFRESH,
                Duration.ofDays(props.refreshTokenValidityDays()));
    }

    private String createToken(Long userId, String email, String type, Duration validity) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validity.toMillis());

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuer(props.issuer())
                .issuedAt(now)
                .expiration(expiry)
                .claim(CLAIM_TYPE, type);

        if (email != null) {
            builder.claim(CLAIM_EMAIL, email);
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