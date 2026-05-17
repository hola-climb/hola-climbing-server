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
        return new GymSummaryResponse(
                gym.getId(), gym.getName(), gym.getAddress(), gym.getThumbnailUrl(),
                gym.getRegionCode(), gym.getRatingAvg(), gym.getRatingCount());
    }
}
