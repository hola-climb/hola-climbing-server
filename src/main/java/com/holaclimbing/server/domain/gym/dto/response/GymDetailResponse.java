package com.holaclimbing.server.domain.gym.dto.response;

import com.holaclimbing.server.domain.gym.domain.Gym;
import com.holaclimbing.server.domain.gym.dto.DayHours;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record GymDetailResponse(
        Long id,
        String name,
        String address,
        Double lat,
        Double lng,
        String description,
        String phone,
        String website,
        String thumbnailUrl,
        Map<String, DayHours> businessHours,
        String regionCode,
        BigDecimal ratingAvg,
        int ratingCount,
        String status,
        List<GymPhotoResponse> photos
) {
    public static GymDetailResponse of(Gym gym, Map<String, DayHours> businessHours,
                                       List<GymPhotoResponse> photos) {
        return new GymDetailResponse(
                gym.getId(), gym.getName(), gym.getAddress(), gym.getLat(), gym.getLng(),
                gym.getDescription(), gym.getPhone(), gym.getWebsite(), gym.getThumbnailUrl(),
                businessHours, gym.getRegionCode(), gym.getRatingAvg(), gym.getRatingCount(),
                gym.getStatus(), photos);
    }
}
