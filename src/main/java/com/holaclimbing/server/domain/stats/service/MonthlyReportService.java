package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportAvailablePeriodsResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportResponse;

import java.time.YearMonth;

public interface MonthlyReportService {

    MonthlyReportResponse getMonthlyReport(Long userId, YearMonth month, Long gymId);

    MonthlyReportResponse generateMonthlyReport(Long userId, YearMonth month, Long gymId);

    MonthlyReportAvailablePeriodsResponse getAvailablePeriods(Long userId);
}
