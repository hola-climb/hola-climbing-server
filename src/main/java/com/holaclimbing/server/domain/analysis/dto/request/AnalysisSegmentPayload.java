package com.holaclimbing.server.domain.analysis.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 분석 결과 수신 요청의 세그먼트 한 건.
 */
public record AnalysisSegmentPayload(
        @JsonAlias("sequence_index")
        @NotNull @PositiveOrZero Integer sequenceIndex,
        @JsonAlias("start_time_ms")
        Integer startTimeMs,
        @JsonAlias("end_time_ms")
        Integer endTimeMs,
        @NotBlank String technique,
        @JsonAlias("is_dynamic")
        Boolean isDynamic,
        Float confidence
) {
}
