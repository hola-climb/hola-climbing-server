package com.holaclimbing.server.domain.gym.dto.response;

import com.holaclimbing.server.domain.gym.domain.Gym;

import java.math.BigDecimal;
import java.util.List;

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
        String regionCode,
        BigDecimal ratingAvg,
        int ratingCount,
        String status,
        List<GymPhotoResponse> photos
) {
    public static GymDetailResponse of(Gym gym, List<GymPhotoResponse> photos) {
        return new GymDetailResponse(
                gym.getId(), gym.getName(), gym.getAddress(), gym.getLat(), gym.getLng(),
                gym.getDescription(), gym.getPhone(), gym.getWebsite(), gym.getThumbnailUrl(),
                gym.getRegionCode(), gym.getRatingAvg(), gym.getRatingCount(), gym.getStatus(), photos);
    }
}
