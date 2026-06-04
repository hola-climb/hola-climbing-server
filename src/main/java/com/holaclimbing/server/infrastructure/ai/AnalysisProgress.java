package com.holaclimbing.server.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.Instant;

/**
 * 분석 진행 이벤트. Python 워커가 Redis Pub/Sub `analysis:progress` 채널에 발행하고,
 * Spring이 구독해 상태 저장소·SSE로 전달한다.
 */
public record AnalysisProgress(
        @JsonAlias("video_id")
        Long videoId,
        AnalysisStage stage,
        String message,
        @JsonAlias("updated_at")
        Instant updatedAt
) {

    public static AnalysisProgress of(Long videoId, AnalysisStage stage, String message) {
        return new AnalysisProgress(videoId, stage, message, Instant.now());
    }
}
