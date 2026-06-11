package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.dto.request.CreateClimbingLogRequest;
import com.holaclimbing.server.domain.stats.dto.request.UpdateClimbingLogRequest;
import com.holaclimbing.server.domain.stats.dto.response.CalendarDayResponse;
import com.holaclimbing.server.domain.stats.dto.response.ClimbingLogResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyCalendarResponse;

import java.time.LocalDate;
import java.util.List;

public interface ClimbingLogService {

    /** 클라이밍 기록 작성. */
    ClimbingLogResponse createLog(Long userId, CreateClimbingLogRequest request);

    /** 기록 단건 조회 (작성자만). */
    ClimbingLogResponse getLog(Long userId, Long logId);

    /** 기록 수정 (작성자만). */
    ClimbingLogResponse updateLog(Long userId, Long logId, UpdateClimbingLogRequest request);

    /** 기록 삭제 (작성자만, 소프트 삭제). */
    void deleteLog(Long userId, Long logId);

    /** 월간 달력 — 날짜별 기록 수·푼 문제 수 요약. */
    MonthlyCalendarResponse getMonthlyCalendar(Long userId, int year, int month);

    /** 특정 날짜의 기록 목록. */
    List<ClimbingLogResponse> getLogsByDate(Long userId, LocalDate date);
}
