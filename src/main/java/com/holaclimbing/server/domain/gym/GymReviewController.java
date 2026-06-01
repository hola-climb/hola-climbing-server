package com.holaclimbing.server.domain.gym;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.dto.request.CreateReviewRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateReviewRequest;
import com.holaclimbing.server.domain.gym.dto.response.GymReviewResponse;
import com.holaclimbing.server.domain.gym.service.GymReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 암장 리뷰 API. 조회는 공개, 작성·수정·삭제는 인증이 필요하다.
 */
@RestController
@RequestMapping("/api/gyms")
@RequiredArgsConstructor
@Validated
public class GymReviewController {

    private final GymReviewService gymReviewService;

    @PostMapping("/{gymId}/reviews")
    public ResponseEntity<ApiResponse<GymReviewResponse>> createReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gymId,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(gymReviewService.createReview(userId, gymId, request)));
    }

    @GetMapping("/{gymId}/reviews")
    public ApiResponse<PageResponse<GymReviewResponse>> getReviews(
            @PathVariable Long gymId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(gymReviewService.getReviews(gymId, page, size));
    }

    @PatchMapping("/reviews/{reviewId}")
    public ApiResponse<GymReviewResponse> updateReview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateReviewRequest request) {
        return ApiResponse.success(gymReviewService.updateReview(userId, reviewId, request));
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<Void> deleteReview(@AuthenticationPrincipal Long userId,
                                          @PathVariable Long reviewId) {
        gymReviewService.deleteReview(userId, reviewId);
        return ApiResponse.success();
    }
}
