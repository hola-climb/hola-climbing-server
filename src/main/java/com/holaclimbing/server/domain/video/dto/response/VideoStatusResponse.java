package com.holaclimbing.server.domain.video.dto.response;

import com.holaclimbing.server.infrastructure.ai.AnalysisProgress;
import com.holaclimbing.server.infrastructure.ai.AnalysisProgressView;

/**
 * 영상 분석 진행 상태 응답 (F-02-09).
 * status: pending | analyzing | done | failed
 */
public record VideoStatusResponse(
        Long id,
        Long videoId,
        String status,
        int progress,
        String stage,
        Integer estimatedSecondsRemaining
) {

    public static VideoStatusResponse from(Long videoId, String videoStatus, AnalysisProgress progress) {
        AnalysisProgressView view = AnalysisProgressView.from(videoId, videoStatus, progress);
        return new VideoStatusResponse(
                videoId,
                videoId,
                view.status(),
                view.progress(),
                view.stage(),
                view.estimatedSecondsRemaining()
        );
    }
}
