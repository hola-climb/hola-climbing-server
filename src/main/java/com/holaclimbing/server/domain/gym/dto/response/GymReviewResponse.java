package com.holaclimbing.server.domain.gym.dto.response;

import com.holaclimbing.server.domain.gym.domain.GymReview;

import java.time.LocalDateTime;

public record GymReviewResponse(
        Long id,
        Long gymId,
        Long userId,
        int rating,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GymReviewResponse of(GymReview review) {
        return new GymReviewResponse(
                review.getId(), review.getGymId(), review.getUserId(), review.getRating(),
                review.getContent(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
