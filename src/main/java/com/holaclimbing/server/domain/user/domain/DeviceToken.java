package com.holaclimbing.server.domain.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * FCM 디바이스 토큰. device_tokens 테이블 매핑.
 * (user_id, token) 쌍이 유일하며 token 자체에 UNIQUE 제약이 있다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceToken {

    private Long id;
    private Long userId;
    private String token;
    private String platform;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
