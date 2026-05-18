package com.holaclimbing.server.domain.stats;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.stats.dto.response.TechniqueStatsResponse;
import com.holaclimbing.server.domain.stats.dto.response.UserStatsResponse;
import com.holaclimbing.server.domain.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 클라이밍 통계 API. 내 통계는 인증 필요, 특정 사용자 통계는 공개.
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/me")
    public ApiResponse<UserStatsResponse> getMyStats(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(statsService.getUserStats(userId));
    }

    @GetMapping("/me/techniques")
    public ApiResponse<TechniqueStatsResponse> getMyTechniqueStats(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(statsService.getTechniqueStats(userId));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<UserStatsResponse> getUserStats(@PathVariable Long userId) {
        return ApiResponse.success(statsService.getUserStats(userId));
    }
}
