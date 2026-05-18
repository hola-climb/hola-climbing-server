package com.holaclimbing.server.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 인증 요청. 인증 메일에 담긴 토큰을 검증한다.
 */
public record VerifyEmailRequest(
        @NotBlank String token
) {
}
