package com.holaclimbing.server.domain.recommendation;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.CursorPageResponse;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedGymResponse;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedVideoResponse;
import com.holaclimbing.server.domain.recommendation.service.RecommendationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 추천 API. 홈 피드(팔로잉 + 추천 영상 혼합)를 제공한다. 인증이 필요하다.
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Validated
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/videos")
    public ApiResponse<CursorPageResponse<RecommendedVideoResponse>> getVideoFeed(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String nextCursor,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(recommendationService.getVideoFeed(
                userId, resolveCursor(cursor, nextCursor), size));
    }

    private String resolveCursor(String cursor, String nextCursor) {
        return cursor == null || cursor.isBlank() ? nextCursor : cursor;
    }

    @GetMapping("/gyms")
    public ApiResponse<List<RecommendedGymResponse>> getNearbyGyms(
            @AuthenticationPrincipal Long userId,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "10") @Positive double radius,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(recommendationService.getNearbyGyms(userId, lat, lng, radius, size));
    }
}
