package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.monthly-report.scheduler", name = "enabled", havingValue = "true")
public class MonthlyReportScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MonthlyReportService monthlyReportService;
    private final UserMapper userMapper;

    @Scheduled(cron = "${app.monthly-report.scheduler.cron}", zone = "Asia/Seoul")
    public void generatePreviousMonthReports() {
        YearMonth targetMonth = YearMonth.now(KST).minusMonths(1);
        for (Long userId : userMapper.findActiveUserIdsForMonthlyReport()) {
            try {
                monthlyReportService.generateMonthlyReport(userId, targetMonth, null);
            } catch (Exception e) {
                log.warn("monthly report generation failed. userId={}, period={}", userId, targetMonth, e);
            }
        }
    }
}
