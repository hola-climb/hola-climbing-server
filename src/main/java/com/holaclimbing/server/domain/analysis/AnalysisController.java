package com.holaclimbing.server.domain.analysis;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisFeedbackRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.response.FeedbackResponse;
import com.holaclimbing.server.domain.analysis.dto.response.VideoAnalysisResponse;
import com.holaclimbing.server.domain.analysis.service.AnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 분석 API.
 * 조회/재시도/피드백은 영상에 종속된 경로(/api/videos/{id}/analysis*),
 * 결과 수신(ingest)은 AI 워커(Python)가 호출하는 서버 간 콜백(/api/analysis/videos/{id})이다.
 * 실제 포즈 추정·동작 분류는 Python FastAPI가 담당하며 Spring은 결과만 저장·제공한다.
 */
@RestController
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/api/videos/{videoId}/analysis")
    public ApiResponse<VideoAnalysisResponse> getAnalysis(@PathVariable Long videoId) {
        return ApiResponse.success(analysisService.getAnalysis(videoId));
    }

    @PostMapping("/api/videos/{videoId}/analysis/retry")
    public ApiResponse<VideoAnalysisResponse> retryAnalysis(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long videoId) {
        return ApiResponse.success(analysisService.retryAnalysis(userId, videoId));
    }

    @PostMapping("/api/videos/{videoId}/analysis/feedback")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitFeedback(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long videoId,
            @Valid @RequestBody AnalysisFeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(analysisService.submitFeedback(userId, videoId, request)));
    }

    /** AI 워커(Python) → Spring 결과 수신 콜백. 명세 외 내부 엔드포인트. */
    @PostMapping("/api/analysis/videos/{videoId}")
    public ApiResponse<VideoAnalysisResponse> ingestResult(
            @PathVariable Long videoId,
            @Valid @RequestBody AnalysisIngestRequest request) {
        return ApiResponse.success(analysisService.ingestResult(videoId, request));
    }
}
