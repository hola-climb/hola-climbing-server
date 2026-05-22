package com.holaclimbing.server.domain.gym.dto.request;

import com.holaclimbing.server.domain.gym.dto.DayHours;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 암장 요일별 운영시간 수정 요청.
 * 키는 요일(mon~sun), 값은 운영시간(휴무일은 null). 전달된 맵으로 전체 치환한다.
 */
public record UpdateBusinessHoursRequest(
        @NotNull Map<String, DayHours> businessHours
) {
}
