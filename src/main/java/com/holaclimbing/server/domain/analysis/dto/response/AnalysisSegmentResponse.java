package com.holaclimbing.server.domain.analysis.dto.response;

import com.holaclimbing.server.domain.analysis.domain.AnalysisResult;

public record AnalysisSegmentResponse(
        int sequenceIndex,
        Integer startTimeMs,
        Integer endTimeMs,
        String technique,
        Boolean isDynamic,
        Float confidence
) {
    public static AnalysisSegmentResponse of(AnalysisResult result) {
        return new AnalysisSegmentResponse(
                result.getSequenceIndex(), result.getStartTimeMs(), result.getEndTimeMs(),
                result.getTechnique(), result.getIsDynamic(), result.getConfidence());
    }
}
