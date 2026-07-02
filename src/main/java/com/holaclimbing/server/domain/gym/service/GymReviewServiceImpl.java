package com.holaclimbing.server.domain.gym.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.domain.GymReview;
import com.holaclimbing.server.domain.gym.dto.request.CreateReviewRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateReviewRequest;
import com.holaclimbing.server.domain.gym.dto.response.GymReviewResponse;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import com.holaclimbing.server.domain.gym.mapper.GymReviewMapper;
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GymReviewServiceImpl implements GymReviewService {

    private final GymReviewMapper reviewMapper;
    private final GymMapper gymMapper;
    private final GcsStorageService gcsStorageService;

    @Override
    @Transactional
    public GymReviewResponse createReview(Long userId, Long gymId, CreateReviewRequest request) {
        if (gymMapper.findById(gymId) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        if (reviewMapper.existsByGymAndUser(gymId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }
        GymReview review = GymReview.builder()
                .gymId(gymId)
                .userId(userId)
                .rating(request.rating())
                .content(request.content())
                .build();
        reviewMapper.insert(review);
        reviewMapper.recalcGymRating(gymId);
        return toResponse(reviewMapper.findById(review.getId()));
    }

    @Override
    public PageResponse<GymReviewResponse> getReviews(Long gymId, int page, int size) {
        if (gymMapper.findById(gymId) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        long total = reviewMapper.countByGymId(gymId);
        List<GymReviewResponse> content = reviewMapper.findByGymId(gymId, size, page * size)
                .stream().map(this::toResponse).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public GymReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request) {
        GymReview review = findOwnedReview(userId, reviewId);
        reviewMapper.update(reviewId, request.rating(), request.content());
        reviewMapper.recalcGymRating(review.getGymId());
        return toResponse(reviewMapper.findById(reviewId));
    }

    @Override
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        GymReview review = findOwnedReview(userId, reviewId);
        reviewMapper.delete(reviewId);
        reviewMapper.recalcGymRating(review.getGymId());
    }

    private GymReview findOwnedReview(Long userId, Long reviewId) {
        GymReview review = reviewMapper.findById(reviewId);
        if (review == null) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return review;
    }

    private GymReviewResponse toResponse(GymReview review) {
        return GymReviewResponse.of(review, resolveProfileImage(review.getProfileImage()));
    }

    private String resolveProfileImage(String storedProfileImage) {
        if (storedProfileImage == null || storedProfileImage.isBlank()) {
            return null;
        }
        if (storedProfileImage.startsWith("http://") || storedProfileImage.startsWith("https://")) {
            return storedProfileImage;
        }
        return gcsStorageService.createReadUrl(storedProfileImage);
    }
}
