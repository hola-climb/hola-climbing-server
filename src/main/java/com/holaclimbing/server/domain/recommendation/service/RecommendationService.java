package com.holaclimbing.server.domain.recommendation.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedVideoResponse;

public interface RecommendationService {

    /** 홈 피드 추천 — 팔로잉 영상 + 추천 영상 혼합. */
    PageResponse<RecommendedVideoResponse> getVideoFeed(Long userId, int page, int size);
}
