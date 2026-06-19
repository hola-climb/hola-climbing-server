package com.holaclimbing.server.domain.recommendation.dto;

import com.holaclimbing.server.domain.video.domain.Video;
import java.time.OffsetDateTime;

public class RecommendationCursor {

    private final int distanceNullRank;
    private final Double rankingDistance;
    private final int followingRank;
    private final OffsetDateTime createdAt;
    private final Long id;

    public RecommendationCursor(
            int distanceNullRank,
            Double rankingDistance,
            int followingRank,
            OffsetDateTime createdAt,
            Long id) {
        this.distanceNullRank = distanceNullRank;
        this.rankingDistance = rankingDistance;
        this.followingRank = followingRank;
        this.createdAt = createdAt;
        this.id = id;
    }

    public static RecommendationCursor from(Video video) {
        return new RecommendationCursor(
                video.getDistanceNullRank(),
                video.getRankingDistance(),
                video.getFollowingRank(),
                video.getCreatedAt(),
                video.getId());
    }

    public int getDistanceNullRank() {
        return distanceNullRank;
    }

    public Double getRankingDistance() {
        return rankingDistance;
    }

    public int getFollowingRank() {
        return followingRank;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getId() {
        return id;
    }
}
