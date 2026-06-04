package com.holaclimbing.server.domain.analysis.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * AI 워커(Python)가 분석을 마친 뒤 결과를 주입하는 요청.
 * status는 'done' 또는 'failed' — done이면 segments로 기존 결과를 대체한다.
 * model_version 예: rule_v1 | lstm_v1 | videomae_v1.
 */
public record AnalysisIngestRequest(
        @NotEmpty String status,
        @JsonAlias("model_version")
        String modelVersion,
        @Valid List<AnalysisSegmentPayload> segments
) {
}
