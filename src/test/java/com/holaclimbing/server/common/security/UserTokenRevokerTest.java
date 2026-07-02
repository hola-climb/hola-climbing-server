package com.holaclimbing.server.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserTokenRevokerTest {

    @Test
    @DisplayName("토큰 발급 시각이 revoke 마커와 같은 millisecond여도 거부한다")
    void isRevoked_rejectsTokenIssuedAtSameMillisAsMarker() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:revoke:user:42")).thenReturn("1700000000000");

        UserTokenRevoker revoker = new UserTokenRevoker(redis);

        assertThat(revoker.isRevoked(42L, 1_700_000_000_000L)).isTrue();
    }

    @Test
    @DisplayName("초 단위로 저장된 기존 revoke 마커도 millisecond 기준으로 해석한다")
    void isRevoked_supportsLegacySecondMarker() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:revoke:user:42")).thenReturn("1000");

        UserTokenRevoker revoker = new UserTokenRevoker(redis);

        assertThat(revoker.isRevoked(42L, 1_000_999L)).isTrue();
        assertThat(revoker.isRevoked(42L, 1_001_000L)).isFalse();
    }
}
