package com.holaclimbing.server.domain.analysis.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 영상 단위 분석 결과 피드백 요청.
 * 사용자가 확인한 7가지 동작 포함 여부와 영상의 dynamic/static 여부를 최종값으로 반영한다.
 */
public record AnalysisFeedbackRequest(
        @NotNull List<@NotBlank String> techniques,
        @JsonAlias("is_dynamic")
        @NotNull Boolean isDynamic,
        String note
) {
}
