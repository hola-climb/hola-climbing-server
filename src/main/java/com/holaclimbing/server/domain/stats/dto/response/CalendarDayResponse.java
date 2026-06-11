package com.holaclimbing.server.domain.stats.dto.response;

import java.time.LocalDate;

/**
 * 달력의 하루치 요약. 해당 날짜의 기록 수, 푼 문제 총합, 영상 수.
 */
public record CalendarDayResponse(
        LocalDate date,
        int logCount,
        int totalProblems,
        int videoCount
) {
}
