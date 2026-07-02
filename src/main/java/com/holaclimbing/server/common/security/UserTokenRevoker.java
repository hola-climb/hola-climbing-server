package com.holaclimbing.server.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 사용자 단위 토큰 무효화 마커.
 *
 * <p>비밀번호 재설정·회원 탈퇴 등 보안 이벤트 발생 시 호출하면 해당 사용자의 모든 활성 토큰을
 * 즉시 무효화한다. 동작 원리는 사용자 id별 "revokedBefore" 타임스탬프를 Redis에 저장하고,
 * {@link JwtAuthenticationFilter}가 요청마다 토큰의 {@code iat}(issued-at)가 이 값보다
 * 이르면 거부하는 방식이다.</p>
 *
 * <p>키 TTL은 refresh 토큰 최대 수명(14일)과 같게 둔다 — 그 이후 발급된 토큰은 어차피
 * iat가 마커보다 늦으므로 자연스럽게 통과한다.</p>
 */
@Component
@RequiredArgsConstructor
public class UserTokenRevoker {

    private static final String PREFIX = "auth:revoke:user:";
    /** refresh 토큰 최대 수명에 맞춘 보존 기간. 이보다 더 보관해도 의미가 없다. */
    private static final Duration RETENTION = Duration.ofDays(30);

    private final StringRedisTemplate redis;

    /** 해당 사용자의 모든 기존 토큰을 무효화(이 시점 이전 발급된 모든 토큰 거부). */
    public void revokeAllFor(Long userId) {
        if (userId == null) {
            return;
        }
        redis.opsForValue().set(PREFIX + userId, String.valueOf(Instant.now().toEpochMilli()), RETENTION);
    }

    /** 토큰 발급 시각(epoch millis)이 revoke 마커 이전이면 true (= 거부 대상). */
    public boolean isRevoked(Long userId, long issuedAtEpochMillis) {
        if (userId == null) {
            return false;
        }
        String marker = redis.opsForValue().get(PREFIX + userId);
        if (marker == null) {
            return false;
        }
        try {
            long markerMillis = parseMarkerMillis(marker);
            return issuedAtEpochMillis <= markerMillis;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private long parseMarkerMillis(String marker) {
        long value = Long.parseLong(marker);
        if (value < 1_000_000_000_000L) {
            return Instant.ofEpochSecond(value).toEpochMilli() + 999;
        }
        return value;
    }
}
