package com.holaclimbing.server.domain.user.dto.response;

import com.holaclimbing.server.domain.user.domain.User;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long userId,
        String nickname,
        String profileImage,
        String bio,
        long followerCount,
        long followingCount,
        boolean isFollowing,
        LocalDateTime createdAt
) {
    public static UserProfileResponse of(User user, long followerCount, long followingCount, boolean isFollowing) {
        return new UserProfileResponse(
                user.getId(), user.getNickname(), user.getProfileImage(), user.getBio(),
                followerCount, followingCount, isFollowing, user.getCreatedAt());
    }
}
