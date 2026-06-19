package com.holaclimbing.server.domain.gym.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 암장 리뷰. gym_reviews 테이블 매핑. 사용자당 암장 1개 리뷰.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymReview {

    private Long id;
    private Long gymId;
    private Long userId;
    private Integer rating;
    private String content;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
