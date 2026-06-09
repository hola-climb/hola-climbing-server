package com.holaclimbing.server.domain.stats.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

/**
 * 클라이밍 기록 작성 요청. grade_counts는 난이도별 푼 문제 수 ({"빨강": 3, ...}).
 */
public record CreateClimbingLogRequest(
        @NotNull Long gymId,
        @NotNull LocalDate climbedOn,
        @NotEmpty Map<@NotBlank String, @PositiveOrZero Integer> gradeCounts,
        @Size(max = 1000) String memo
) {
}
