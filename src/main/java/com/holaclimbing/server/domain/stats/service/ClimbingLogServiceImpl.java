package com.holaclimbing.server.domain.stats.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import com.holaclimbing.server.domain.stats.domain.CalendarVideoStats;
import com.holaclimbing.server.domain.stats.domain.ClimbingLog;
import com.holaclimbing.server.domain.stats.dto.request.CreateClimbingLogRequest;
import com.holaclimbing.server.domain.stats.dto.request.UpdateClimbingLogRequest;
import com.holaclimbing.server.domain.stats.dto.response.CalendarDayResponse;
import com.holaclimbing.server.domain.stats.dto.response.ClimbingLogResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyCalendarResponse;
import com.holaclimbing.server.domain.stats.mapper.ClimbingLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClimbingLogServiceImpl implements ClimbingLogService {

    private static final TypeReference<Map<String, Integer>> GRADE_COUNTS_TYPE =
            new TypeReference<>() {
            };

    private final ClimbingLogMapper logMapper;
    private final GymMapper gymMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClimbingLogResponse createLog(Long userId, CreateClimbingLogRequest request) {
        requireGym(request.gymId());
        ClimbingLog log = ClimbingLog.builder()
                .userId(userId)
                .gymId(request.gymId())
                .climbedOn(request.climbedOn())
                .gradeCounts(writeGradeCounts(request.gradeCounts()))
                .memo(request.memo())
                .build();
        logMapper.insert(log);
        return toResponse(logMapper.findById(log.getId()));
    }

    @Override
    public ClimbingLogResponse getLog(Long userId, Long logId) {
        return toResponse(findOwnedLog(userId, logId));
    }

    @Override
    @Transactional
    public ClimbingLogResponse updateLog(Long userId, Long logId, UpdateClimbingLogRequest request) {
        findOwnedLog(userId, logId);
        requireGym(request.gymId());
        logMapper.update(logId, request.gymId(), request.climbedOn(),
                writeGradeCounts(request.gradeCounts()), request.memo());
        return toResponse(logMapper.findById(logId));
    }

    @Override
    @Transactional
    public void deleteLog(Long userId, Long logId) {
        findOwnedLog(userId, logId);
        logMapper.softDelete(logId);
    }

    @Override
    public MonthlyCalendarResponse getMonthlyCalendar(Long userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        Map<LocalDate, DayAggregate> byDate = new TreeMap<>();
        int totalProblems = 0;
        int totalGymVisits = 0;
        for (ClimbingLog log : logMapper.findByUserAndPeriod(userId, from, to)) {
            DayAggregate agg = byDate.computeIfAbsent(log.getClimbedOn(), d -> new DayAggregate());
            int problems = sum(parseGradeCounts(log.getGradeCounts()));
            agg.logCount++;
            agg.totalProblems += problems;
            totalProblems += problems;
            totalGymVisits++;
        }
        int totalVideos = 0;
        long totalVideoDurationSeconds = 0;
        for (CalendarVideoStats stats : logMapper.findVideoStatsByUserAndPeriod(userId, from, to)) {
            DayAggregate agg = byDate.computeIfAbsent(stats.getDate(), d -> new DayAggregate());
            agg.videoCount = stats.getVideoCount();
            totalVideos += stats.getVideoCount();
            totalVideoDurationSeconds += stats.getTotalDurationSeconds();
        }
        List<CalendarDayResponse> days = byDate.entrySet().stream()
                .map(e -> new CalendarDayResponse(
                        e.getKey(), e.getValue().logCount, e.getValue().totalProblems, e.getValue().videoCount))
                .toList();
        return new MonthlyCalendarResponse(
                year, month, totalVideos, totalProblems, totalVideoDurationSeconds, totalGymVisits, days);
    }

    @Override
    public List<ClimbingLogResponse> getLogsByDate(Long userId, LocalDate date) {
        return logMapper.findByUserAndDate(userId, date).stream()
                .map(this::toResponse).toList();
    }

    private ClimbingLog findOwnedLog(Long userId, Long logId) {
        ClimbingLog log = logMapper.findById(logId);
        if (log == null) {
            throw new BusinessException(ErrorCode.CLIMBING_LOG_NOT_FOUND);
        }
        if (!log.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return log;
    }

    private void requireGym(Long gymId) {
        if (gymMapper.findById(gymId) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
    }

    private ClimbingLogResponse toResponse(ClimbingLog log) {
        return ClimbingLogResponse.of(log, parseGradeCounts(log.getGradeCounts()));
    }

    private String writeGradeCounts(Map<String, Integer> counts) {
        try {
            return objectMapper.writeValueAsString(counts);
        } catch (Exception e) {
            throw new IllegalStateException("grade_counts 직렬화 실패", e);
        }
    }

    private Map<String, Integer> parseGradeCounts(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, GRADE_COUNTS_TYPE);
        } catch (Exception e) {
            log.warn("grade_counts 파싱 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    private int sum(Map<String, Integer> counts) {
        return counts.values().stream().filter(v -> v != null).mapToInt(Integer::intValue).sum();
    }

    private static class DayAggregate {
        private int logCount;
        private int totalProblems;
        private int videoCount;
    }
}
