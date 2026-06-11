package com.holaclimbing.server.domain.stats;

import static com.holaclimbing.server.common.exception.error.ErrorCode.*;

import com.holaclimbing.server.common.exception.docs.ApiErrorCodes;
import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.stats.dto.response.ClimbingLogResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyCalendarResponse;
import com.holaclimbing.server.domain.stats.dto.response.TechniqueStatsResponse;
import com.holaclimbing.server.domain.stats.dto.response.UserStatsResponse;
import com.holaclimbing.server.domain.stats.service.ClimbingLogService;
import com.holaclimbing.server.domain.stats.service.StatsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 클라이밍 통계·달력 API. 내 통계·달력은 인증 필요, 특정 사용자 통계는 공개.
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Validated
public class StatsController {

    private final StatsService statsService;
    private final ClimbingLogService climbingLogService;

    @ApiErrorCodes({USER_NOT_FOUND})
    @GetMapping("/me")
    public ApiResponse<UserStatsResponse> getMyStats(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(statsService.getUserStats(userId));
    }

    @ApiErrorCodes({USER_NOT_FOUND})
    @GetMapping("/me/techniques")
    public ApiResponse<TechniqueStatsResponse> getMyTechniqueStats(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(statsService.getTechniqueStats(userId));
    }

    @ApiErrorCodes({USER_NOT_FOUND})
    @GetMapping("/users/{userId}")
    public ApiResponse<UserStatsResponse> getUserStats(@PathVariable Long userId) {
        return ApiResponse.success(statsService.getUserStats(userId));
    }

    /** 월간 달력 — 날짜별 클라이밍 기록 요약. */
    @GetMapping("/me/calendar")
    public ApiResponse<MonthlyCalendarResponse> getMyCalendar(
            @AuthenticationPrincipal Long userId,
            @RequestParam @Min(2000) @Max(2100) int year,
            @RequestParam @Min(1) @Max(12) int month) {
        return ApiResponse.success(climbingLogService.getMonthlyCalendar(userId, year, month));
    }

    /** 특정 날짜의 클라이밍 기록 목록. */
    @GetMapping("/me/calendar/{date}")
    public ApiResponse<List<ClimbingLogResponse>> getMyCalendarByDate(
            @AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(climbingLogService.getLogsByDate(userId, date));
    }
}
