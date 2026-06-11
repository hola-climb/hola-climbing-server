package com.holaclimbing.server.domain.stats.dto.response;

import java.util.List;

/**
 * 월간 달력 응답. days는 기록 또는 영상이 있는 날짜만 오름차순으로 포함한다.
 */
public record MonthlyCalendarResponse(
        int year,
        int month,
        int totalVideos,
        int totalProblems,
        long totalVideoDurationSeconds,
        int totalGymVisits,
        List<CalendarDayResponse> days
) {
}
