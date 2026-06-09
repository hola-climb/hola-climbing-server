package com.holaclimbing.server.domain.stats.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

/**
 * 클라이밍 기록 수정 요청. 전달된 값으로 전체 치환한다.
 */
public record UpdateClimbingLogRequest(
        @NotNull Long gymId,
        @NotNull LocalDate climbedOn,
        @NotEmpty Map<@NotBlank String, @PositiveOrZero Integer> gradeCounts,
        @Size(max = 1000) String memo
) {
}
