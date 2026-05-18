package com.holaclimbing.server.domain.analysis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 분석 결과 수신 요청의 세그먼트 한 건.
 */
public record AnalysisSegmentPayload(
        @NotNull @PositiveOrZero Integer sequenceIndex,
        Integer startTimeMs,
        Integer endTimeMs,
        @NotBlank String technique,
        Boolean isDynamic,
        Float confidence
) {
}
