package com.holaclimbing.server.domain.gym.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.dto.request.CreateReviewRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateReviewRequest;
import com.holaclimbing.server.domain.gym.dto.response.GymReviewResponse;

public interface GymReviewService {

    /** 암장 리뷰 작성. 사용자당 암장 1개 리뷰만 가능. */
    GymReviewResponse createReview(Long userId, Long gymId, CreateReviewRequest request);

    /** 암장 리뷰 목록 조회 (최신순). */
    PageResponse<GymReviewResponse> getReviews(Long gymId, int page, int size);

    /** 리뷰 수정 (작성자만). */
    GymReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request);

    /** 리뷰 삭제 (작성자만). */
    void deleteReview(Long userId, Long reviewId);
}
