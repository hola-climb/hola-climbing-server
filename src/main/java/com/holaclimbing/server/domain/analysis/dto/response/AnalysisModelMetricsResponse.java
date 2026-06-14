package com.holaclimbing.server.domain.analysis.dto.response;

import java.util.Map;

public record AnalysisModelMetricsResponse(
        String modelVersion,
        long feedbackCount,
        long dynamicEvaluatedCount,
        Double dynamicAccuracy,
        Double techniqueExactMatchAccuracy,
        Map<String, AnalysisTechniqueMetricResponse> perTechnique
) {
}
