package com.holaclimbing.server.domain.analysis.dto.response;

import java.util.List;

/**
 * 영상 분석 결과 응답.
 * status는 영상의 분석 상태(pending/done/failed),
 * techniques/isDynamic은 세그먼트 비율 계산이 아니라 영상 단위 대표 결과다.
 */
public record VideoAnalysisResponse(
        Long videoId,
        String status,
        String modelVersion,
        List<String> techniques,
        Boolean isDynamic,
        Float dynamicProbability,
        boolean feedbackApplied
) {
}
