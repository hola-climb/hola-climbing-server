package com.holaclimbing.server.domain.analysis.dto.response;

import com.holaclimbing.server.domain.analysis.domain.AnalysisResult;

import java.util.List;

/**
 * 영상 분석 결과 응답.
 * status는 영상의 분석 상태(pending/analyzing/done/failed),
 * segments는 동작별 분류 결과 시퀀스.
 */
public record VideoAnalysisResponse(
        Long videoId,
        String status,
        String modelVersion,
        List<AnalysisSegmentResponse> segments
) {
    public static VideoAnalysisResponse of(Long videoId, String status, List<AnalysisResult> results) {
        String modelVersion = results.isEmpty() ? null : results.getFirst().getModelVersion();
        List<AnalysisSegmentResponse> segments = results.stream()
                .map(AnalysisSegmentResponse::of)
                .toList();
        return new VideoAnalysisResponse(videoId, status, modelVersion, segments);
    }
}
