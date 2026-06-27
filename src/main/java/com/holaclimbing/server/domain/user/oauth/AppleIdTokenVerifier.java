package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;

@Component
public class AppleIdTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AppleJwksClient jwksClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppleIdTokenVerifier(AppleJwksClient jwksClient) {
        this.jwksClient = jwksClient;
    }

    public AppleIdTokenClaims verify(String idToken, OAuthProperties.Provider provider, String expectedNonce) {
        validateRequest(idToken, provider);

        String keyId = extractKeyId(idToken);
        PublicKey publicKey = jwksClient.getKey(provider.jwksUri(), keyId);
        Claims claims = parseClaims(idToken, publicKey);
        validateClaims(claims, provider, expectedNonce);

        return new AppleIdTokenClaims(
                claims.getSubject(),
                claims.get("email", String.class),
                true
        );
    }

    private void validateRequest(String idToken, OAuthProperties.Provider provider) {
        if (!StringUtils.hasText(idToken)
                || provider == null
                || !StringUtils.hasText(provider.clientId())
                || !StringUtils.hasText(provider.jwksUri())) {
            throw authorizationFailure();
        }
    }

    private String extractKeyId(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw authorizationFailure();
            }
            byte[] decodedHeader = Base64.getUrlDecoder().decode(parts[0]);
            JsonNode header = objectMapper.readTree(new String(decodedHeader, StandardCharsets.UTF_8));
            String keyId = header.path("kid").asText();
            if (!StringUtils.hasText(keyId)) {
                throw authorizationFailure();
            }
            return keyId;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw authorizationFailure();
        }
    }

    private Claims parseClaims(String idToken, PublicKey publicKey) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw authorizationFailure();
        }
    }

    private void validateClaims(Claims claims, OAuthProperties.Provider provider, String expectedNonce) {
        if (claims.getAudience() == null || !claims.getAudience().contains(provider.clientId())) {
            throw authorizationFailure();
        }
        String tokenNonce = claims.get("nonce", String.class);
        if (!StringUtils.hasText(expectedNonce)
                || !StringUtils.hasText(tokenNonce)
                || !expectedNonce.equals(tokenNonce)) {
            throw authorizationFailure();
        }
        if (!StringUtils.hasText(claims.getSubject())) {
            throw authorizationFailure();
        }
        if (!emailVerified(claims.get("email_verified"))) {
            throw authorizationFailure();
        }
    }

    private boolean emailVerified(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return "true".equals(stringValue);
        }
        return false;
    }

    private BusinessException authorizationFailure() {
        return new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }
}
