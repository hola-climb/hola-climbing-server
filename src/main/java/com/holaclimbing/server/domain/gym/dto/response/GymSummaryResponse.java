package com.holaclimbing.server.domain.gym.dto.response;

import com.holaclimbing.server.domain.gym.domain.Gym;

import java.math.BigDecimal;

public record GymSummaryResponse(
        Long id,
        String name,
        String address,
        String thumbnailUrl,
        String regionCode,
        BigDecimal ratingAvg,
        int ratingCount
) {
    public static GymSummaryResponse from(Gym gym) {
        return from(gym, gym.getThumbnailUrl());
    }

    public static GymSummaryResponse from(Gym gym, String thumbnailUrl) {
        return new GymSummaryResponse(
                gym.getId(), gym.getName(), gym.getAddress(), thumbnailUrl,
                gym.getRegionCode(), gym.getRatingAvg(), gym.getRatingCount());
    }
}
