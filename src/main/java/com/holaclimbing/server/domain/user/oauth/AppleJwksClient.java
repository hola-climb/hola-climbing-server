package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AppleJwksClient {

    private static final long CACHE_TTL_SECONDS = 3600;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<CacheKey, CachedKey> cache = new ConcurrentHashMap<>();

    @Autowired
    public AppleJwksClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this(builder, objectMapper, Clock.systemUTC());
    }

    AppleJwksClient(RestClient.Builder builder, ObjectMapper objectMapper, Clock clock) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PublicKey getKey(String jwksUri, String keyId) {
        validateRequest(jwksUri, keyId);

        CacheKey cacheKey = new CacheKey(jwksUri, keyId);
        CachedKey cached = cache.get(cacheKey);
        Instant now = Instant.now(clock);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.publicKey();
        }

        PublicKey publicKey = fetchKey(jwksUri, keyId);
        cache.put(cacheKey, new CachedKey(publicKey, now.plusSeconds(CACHE_TTL_SECONDS)));
        return publicKey;
    }

    private void validateRequest(String jwksUri, String keyId) {
        if (!StringUtils.hasText(jwksUri) || !StringUtils.hasText(keyId)) {
            throw authorizationFailure();
        }
    }

    private PublicKey fetchKey(String jwksUri, String keyId) {
        try {
            String body = restClient.get()
                    .uri(jwksUri)
                    .retrieve()
                    .body(String.class);
            JsonNode keys = objectMapper.readTree(body).path("keys");
            if (!keys.isArray()) {
                throw authorizationFailure();
            }
            for (JsonNode key : keys) {
                if (keyId.equals(key.path("kid").asText())) {
                    return toRsaPublicKey(key);
                }
            }
            throw authorizationFailure();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw authorizationFailure();
        }
    }

    private PublicKey toRsaPublicKey(JsonNode key) throws Exception {
        if (!"RSA".equals(key.path("kty").asText())
                || !StringUtils.hasText(key.path("n").asText())
                || !StringUtils.hasText(key.path("e").asText())) {
            throw authorizationFailure();
        }
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(key.path("n").asText()));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(key.path("e").asText()));
        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private BusinessException authorizationFailure() {
        return new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }

    private record CacheKey(String jwksUri, String keyId) {
    }

    private record CachedKey(PublicKey publicKey, Instant expiresAt) {
    }
}
