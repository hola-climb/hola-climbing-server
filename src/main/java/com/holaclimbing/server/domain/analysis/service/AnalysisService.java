package com.holaclimbing.server.domain.analysis.service;

import com.holaclimbing.server.domain.analysis.dto.request.AnalysisFeedbackRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.response.AnalysisModelMetricsResponse;
import com.holaclimbing.server.domain.analysis.dto.response.FeedbackResponse;
import com.holaclimbing.server.domain.analysis.dto.response.VideoAnalysisResponse;

public interface AnalysisService {

    /** 영상의 분석 결과 조회. 분석 전이면 영상 대표 결과는 비어 있다. */
    VideoAnalysisResponse getAnalysis(Long videoId, Long viewerId);

    /** AI 워커가 보낸 분석 결과를 저장하고 영상 상태를 갱신한다. */
    VideoAnalysisResponse ingestResult(Long videoId, AnalysisIngestRequest request);

    /** 분석 재시도 (영상 소유자만). 상태를 pending으로 되돌리고 워커에 재요청한다. */
    VideoAnalysisResponse retryAnalysis(Long userId, Long videoId);

    /** 분석 결과 피드백 등록. 최종 결과를 보정하고 재학습 데이터로 사용한다. */
    FeedbackResponse submitFeedback(Long userId, Long videoId, AnalysisFeedbackRequest request);

    /** 피드백이 반영된 결과를 기준으로 특정 모델 버전의 정확도 통계를 산출한다. */
    AnalysisModelMetricsResponse getModelMetrics(String modelVersion);
}
