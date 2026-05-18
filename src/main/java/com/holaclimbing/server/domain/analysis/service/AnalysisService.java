package com.holaclimbing.server.domain.analysis.service;

import com.holaclimbing.server.domain.analysis.dto.request.AnalysisFeedbackRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.response.FeedbackResponse;
import com.holaclimbing.server.domain.analysis.dto.response.VideoAnalysisResponse;

public interface AnalysisService {

    /** 영상의 분석 결과 조회. 분석 전이면 segments는 비어 있다. */
    VideoAnalysisResponse getAnalysis(Long videoId);

    /** AI 워커가 보낸 분석 결과를 저장하고 영상 상태를 갱신한다. */
    VideoAnalysisResponse ingestResult(Long videoId, AnalysisIngestRequest request);

    /** 분석 재시도 (영상 소유자만). 상태를 pending으로 되돌리고 워커에 재요청한다. */
    VideoAnalysisResponse retryAnalysis(Long userId, Long videoId);

    /** 분석 결과 피드백 등록 (F-02-05). 재학습 라벨로 누적된다. */
    FeedbackResponse submitFeedback(Long userId, Long videoId, AnalysisFeedbackRequest request);
}
