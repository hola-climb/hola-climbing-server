package com.holaclimbing.server.domain.user.dto.response;

import com.holaclimbing.server.domain.user.domain.User;

import java.time.LocalDateTime;

public record MyProfileResponse(
        Long userId,
        String email,
        String nickname,
        String profileImage,
        String bio,
        boolean emailVerified,
        long followerCount,
        long followingCount,
        LocalDateTime createdAt
) {
    public static MyProfileResponse of(User user, long followerCount, long followingCount) {
        return new MyProfileResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getProfileImage(),
                user.getBio(), user.isEmailVerified(), followerCount, followingCount, user.getCreatedAt());
    }
}
