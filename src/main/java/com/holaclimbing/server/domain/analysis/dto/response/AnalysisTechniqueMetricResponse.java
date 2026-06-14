package com.holaclimbing.server.domain.analysis.dto.response;

public record AnalysisTechniqueMetricResponse(
        long truePositive,
        long falsePositive,
        long falseNegative,
        long trueNegative,
        Double accuracy,
        Double precision,
        Double recall,
        Double f1
) {
}
