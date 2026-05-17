package com.holaclimbing.server.domain.user.dto.response;

import com.holaclimbing.server.domain.user.domain.User;

public record SignupResponse(
        Long userId,
        String email,
        String nickname,
        boolean emailVerified
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname(), user.isEmailVerified());
    }
}
