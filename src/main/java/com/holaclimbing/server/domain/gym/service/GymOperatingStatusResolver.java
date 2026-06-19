package com.holaclimbing.server.domain.gym.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GymOperatingStatusResolver {

    private static final TypeReference<Map<String, DayHours>> BUSINESS_HOURS_TYPE =
            new TypeReference<>() {
            };

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** business_hours JSONB 문자열을 요일별 운영시간 맵으로 파싱. 비어 있으면 빈 맵. */
    public Map<String, DayHours> parseBusinessHours(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, BUSINESS_HOURS_TYPE);
        } catch (Exception e) {
            log.warn("business_hours 파싱 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    public boolean isOpenNow(Map<String, DayHours> businessHours) {
        if (businessHours == null || businessHours.isEmpty()) {
            return false;
        }

        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalTime currentTime = now.toLocalTime();
        DayHours today = businessHours.get(dayKey(now.getDayOfWeek()));
        if (isOpenDuring(today, currentTime)) {
            return true;
        }

        DayHours yesterday = businessHours.get(dayKey(now.minusDays(1).getDayOfWeek()));
        return isOpenFromPreviousDay(yesterday, currentTime);
    }

    private boolean isOpenDuring(DayHours hours, LocalTime currentTime) {
        TimeRange range = parseRange(hours);
        if (range == null) {
            return false;
        }
        if (range.isAllDay()) {
            return true;
        }
        if (range.isOvernight()) {
            return !currentTime.isBefore(range.open()) || currentTime.isBefore(range.close());
        }
        return !currentTime.isBefore(range.open()) && currentTime.isBefore(range.close());
    }

    private boolean isOpenFromPreviousDay(DayHours hours, LocalTime currentTime) {
        TimeRange range = parseRange(hours);
        return range != null && range.isOvernight() && currentTime.isBefore(range.close());
    }

    private TimeRange parseRange(DayHours hours) {
        if (hours == null || hours.open() == null || hours.close() == null) {
            return null;
        }
        try {
            return new TimeRange(LocalTime.parse(hours.open()), LocalTime.parse(hours.close()));
        } catch (Exception e) {
            log.warn("business_hours 시간 형식 파싱 실패: open={}, close={}", hours.open(), hours.close());
            return null;
        }
    }

    private String dayKey(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "mon";
            case TUESDAY -> "tue";
            case WEDNESDAY -> "wed";
            case THURSDAY -> "thu";
            case FRIDAY -> "fri";
            case SATURDAY -> "sat";
            case SUNDAY -> "sun";
        };
    }

    private record TimeRange(LocalTime open, LocalTime close) {

        private boolean isAllDay() {
            return open.equals(close);
        }

        private boolean isOvernight() {
            return close.isBefore(open);
        }
    }
}
