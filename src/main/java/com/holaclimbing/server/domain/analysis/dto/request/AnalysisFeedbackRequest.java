package com.holaclimbing.server.domain.analysis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 분석 결과 피드백 요청 (F-02-05).
 * isCorrect=false면 correctLabel에 올바른 기술을 담는다.
 * timestampSec/note는 수집하되 라벨 누적에는 technique·isCorrect만 사용한다.
 */
public record AnalysisFeedbackRequest(
        @NotBlank String techniqueLabel,
        Double timestampSec,
        @NotNull Boolean isCorrect,
        String correctLabel,
        String note
) {
}
