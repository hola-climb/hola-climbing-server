package com.holaclimbing.server.domain.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import com.holaclimbing.server.domain.recommendation.domain.RecommendedGym;

import java.math.BigDecimal;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendedGymResponse(
        Long id,
        String name,
        String address,
        String thumbnailUrl,
        String regionCode,
        BigDecimal ratingAvg,
        int ratingCount,
        Map<String, DayHours> businessHours,
        boolean isOpen,
        boolean isFavorite,
        Double distanceKm,
        Double rankingDistance,
        String source
) {
    private static final String SOURCE_STYLE_MATCH = "style_match";
    private static final String SOURCE_NEARBY = "nearby";

    public static RecommendedGymResponse from(RecommendedGym gym) {
        return from(gym, gym.getThumbnailUrl());
    }

    public static RecommendedGymResponse from(RecommendedGym gym, String thumbnailUrl) {
        return from(gym, thumbnailUrl, Map.of(), false, false);
    }

    public static RecommendedGymResponse from(RecommendedGym gym, String thumbnailUrl,
                                              Map<String, DayHours> businessHours,
                                              boolean isOpen, boolean isFavorite) {
        return new RecommendedGymResponse(
                gym.getId(),
                gym.getName(),
                gym.getAddress(),
                thumbnailUrl,
                gym.getRegionCode(),
                gym.getRatingAvg(),
                gym.getRatingCount(),
                businessHours,
                isOpen,
                isFavorite,
                gym.getDistanceKm(),
                gym.getRankingDistance(),
                gym.getRankingDistance() == null ? SOURCE_NEARBY : SOURCE_STYLE_MATCH);
    }
}
