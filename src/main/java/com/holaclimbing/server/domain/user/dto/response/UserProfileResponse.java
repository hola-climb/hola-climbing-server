package com.holaclimbing.server.domain.user.dto.response;

import com.holaclimbing.server.domain.user.domain.User;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        Long userId,
        String nickname,
        String profileImage,
        String bio,
        long followerCount,
        long followingCount,
        boolean isFollowing,
        OffsetDateTime createdAt
) {
    public static UserProfileResponse of(User user, long followerCount, long followingCount, boolean isFollowing) {
        return of(user, followerCount, followingCount, isFollowing, user.getProfileImage());
    }

    public static UserProfileResponse of(User user, long followerCount, long followingCount,
                                         boolean isFollowing, String profileImage) {
        return new UserProfileResponse(
                user.getId(), user.getNickname(), profileImage, user.getBio(),
                followerCount, followingCount, isFollowing, user.getCreatedAt());
    }
}
