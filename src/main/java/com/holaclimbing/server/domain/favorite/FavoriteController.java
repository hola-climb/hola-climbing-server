package com.holaclimbing.server.domain.favorite;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.favorite.service.FavoriteService;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 암장 즐겨찾기 API. 모두 인증이 필요한 본인 전용 엔드포인트.
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Validated
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/gyms/{gymId}")
    public ApiResponse<Void> addFavorite(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long gymId) {
        favoriteService.addFavorite(userId, gymId);
        return ApiResponse.success();
    }

    @DeleteMapping("/gyms/{gymId}")
    public ApiResponse<Void> removeFavorite(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long gymId) {
        favoriteService.removeFavorite(userId, gymId);
        return ApiResponse.success();
    }

    @GetMapping("/gyms")
    public ApiResponse<PageResponse<GymSummaryResponse>> getFavoriteGyms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(favoriteService.getFavoriteGyms(userId, page, size));
    }
}
