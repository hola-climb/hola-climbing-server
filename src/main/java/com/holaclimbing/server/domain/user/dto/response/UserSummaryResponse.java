package com.holaclimbing.server.domain.user.dto.response;

import com.holaclimbing.server.domain.user.domain.User;

public record UserSummaryResponse(
        Long userId,
        String nickname,
        String profileImage,
        String bio
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getNickname(), user.getProfileImage(), user.getBio());
    }
}
