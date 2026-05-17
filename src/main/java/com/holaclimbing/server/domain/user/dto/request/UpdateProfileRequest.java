package com.holaclimbing.server.domain.user.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 프로필 부분 수정 요청. null인 필드는 변경하지 않는다(PATCH 시맨틱).
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 20) String nickname,
        @Size(max = 500) String profileImage,
        @Size(max = 1000) String bio
) {
}
