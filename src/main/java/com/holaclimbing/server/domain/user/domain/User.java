package com.holaclimbing.server.domain.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 회원 엔티티. users 테이블 매핑.
 * style_embedding(vector) 컬럼은 인증 흐름과 무관하여 제외.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;

    // 자체 로그인
    private String email;
    private String passwordHash;
    private boolean emailVerified;
    private String emailVerificationToken;

    // 소셜 로그인
    private String provider;
    private String providerId;

    // 프로필
    private String nickname;
    private String profileImage;
    private String bio;
    private String role;
    private String status;

    // 메타
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
