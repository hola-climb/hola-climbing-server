package com.holaclimbing.server.domain.analysis;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.response.VideoAnalysisResponse;
import com.holaclimbing.server.domain.analysis.service.AnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 분석 결과 API.
 * 조회는 공개, 결과 수신(POST)은 AI 워커(Python)가 호출하는 서버 간 콜백이다.
 * 실제 포즈 추정·동작 분류는 Python FastAPI가 담당하며 Spring은 결과만 저장·제공한다.
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/videos/{videoId}")
    public ApiResponse<VideoAnalysisResponse> getAnalysis(@PathVariable Long videoId) {
        return ApiResponse.success(analysisService.getAnalysis(videoId));
    }

    @PostMapping("/videos/{videoId}")
    public ApiResponse<VideoAnalysisResponse> ingestResult(
            @PathVariable Long videoId,
            @Valid @RequestBody AnalysisIngestRequest request) {
        return ApiResponse.success(analysisService.ingestResult(videoId, request));
    }
}
