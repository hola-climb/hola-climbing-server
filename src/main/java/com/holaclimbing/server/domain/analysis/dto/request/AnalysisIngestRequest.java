package com.holaclimbing.server.domain.analysis.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * AI 워커(Python)가 분석을 마친 뒤 결과를 주입하는 요청.
 * status는 'done' 또는 'failed' — done이면 segments raw 결과와 영상 대표 결과를 대체한다.
 * model_version 예: rule_v1 | lstm_v1 | videomae_v1.
 */
public record AnalysisIngestRequest(
        @NotEmpty String status,
        @JsonAlias("model_version")
        String modelVersion,
        @Valid List<AnalysisSegmentPayload> segments,
        List<String> techniques,
        @JsonAlias("is_dynamic")
        Boolean isDynamic,
        @JsonAlias("dynamic_probability")
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Float dynamicProbability
) {
    public AnalysisIngestRequest(String status, String modelVersion, List<AnalysisSegmentPayload> segments) {
        this(status, modelVersion, segments, null, null, null);
    }
}
