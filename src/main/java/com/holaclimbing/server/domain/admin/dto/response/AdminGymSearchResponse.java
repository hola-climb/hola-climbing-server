package com.holaclimbing.server.domain.admin.dto.response;

import com.holaclimbing.server.domain.gym.domain.Gym;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AdminGymSearchResponse(
        Long id,
        String name,
        String address,
        String regionCode,
        String status,
        Long createdBy,
        BigDecimal ratingAvg,
        int ratingCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static AdminGymSearchResponse from(Gym gym) {
        return new AdminGymSearchResponse(
                gym.getId(),
                gym.getName(),
                gym.getAddress(),
                gym.getRegionCode(),
                gym.getStatus(),
                gym.getCreatedBy(),
                gym.getRatingAvg(),
                gym.getRatingCount(),
                gym.getCreatedAt(),
                gym.getUpdatedAt());
    }
}
