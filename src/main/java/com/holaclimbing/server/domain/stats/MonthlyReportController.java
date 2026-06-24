package com.holaclimbing.server.domain.stats;

import static com.holaclimbing.server.common.exception.error.ErrorCode.*;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.docs.ApiErrorCodes;
import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportAvailablePeriodsResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportResponse;
import com.holaclimbing.server.domain.stats.service.MonthlyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/stats/me/monthly-reports")
@RequiredArgsConstructor
public class MonthlyReportController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MonthlyReportService monthlyReportService;

    @ApiErrorCodes({USER_NOT_FOUND, GYM_NOT_FOUND, INVALID_MONTH, MONTHLY_REPORT_GENERATION_FAILED})
    @GetMapping
    public ApiResponse<MonthlyReportResponse> getMonthlyReport(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long gymId) {
        return ApiResponse.success(monthlyReportService.getMonthlyReport(userId, parseMonth(month), gymId));
    }

    @ApiErrorCodes({USER_NOT_FOUND})
    @GetMapping("/available")
    public ApiResponse<MonthlyReportAvailablePeriodsResponse> getAvailablePeriods(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(monthlyReportService.getAvailablePeriods(userId));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(KST).minusMonths(1);
        }

        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new BusinessException(INVALID_MONTH);
        }
    }
}
