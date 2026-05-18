package com.holaclimbing.server.domain.analysis.service;

import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.response.VideoAnalysisResponse;

public interface AnalysisService {

    /** 영상의 분석 결과 조회. 분석 전이면 segments는 비어 있다. */
    VideoAnalysisResponse getAnalysis(Long videoId);

    /** AI 워커가 보낸 분석 결과를 저장하고 영상 상태를 갱신한다. */
    VideoAnalysisResponse ingestResult(Long videoId, AnalysisIngestRequest request);
}
