package com.holaclimbing.server.domain.gym.dto.response;

import com.holaclimbing.server.domain.gym.domain.Gym;
import com.holaclimbing.server.domain.gym.dto.DayHours;

import java.math.BigDecimal;
import java.util.Map;

public record GymSummaryResponse(
        Long id,
        String name,
        String address,
        String thumbnailUrl,
        String regionCode,
        BigDecimal ratingAvg,
        int ratingCount,
        Map<String, DayHours> businessHours,
        boolean isOpen,
        boolean isFavorite
) {
    public static GymSummaryResponse from(Gym gym) {
        return from(gym, gym.getThumbnailUrl());
    }

    public static GymSummaryResponse from(Gym gym, String thumbnailUrl) {
        return from(gym, thumbnailUrl, Map.of(), false, false);
    }

    public static GymSummaryResponse from(Gym gym, String thumbnailUrl, Map<String, DayHours> businessHours,
                                          boolean isOpen, boolean isFavorite) {
        return new GymSummaryResponse(
                gym.getId(), gym.getName(), gym.getAddress(), thumbnailUrl,
                gym.getRegionCode(), gym.getRatingAvg(), gym.getRatingCount(),
                businessHours, isOpen, isFavorite);
    }
}
