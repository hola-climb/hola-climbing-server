package com.holaclimbing.server.domain.analysis.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.analysis.domain.AnalysisResult;
import com.holaclimbing.server.domain.analysis.domain.Label;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisFeedbackRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.response.FeedbackResponse;
import com.holaclimbing.server.domain.analysis.dto.response.VideoAnalysisResponse;
import com.holaclimbing.server.domain.analysis.mapper.AnalysisMapper;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.mapper.VideoMapper;
import com.holaclimbing.server.infrastructure.ai.AnalysisDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_FAILED = "failed";

    private final AnalysisMapper analysisMapper;
    private final VideoMapper videoMapper;
    private final AnalysisDispatcher analysisDispatcher;

    @Override
    public VideoAnalysisResponse getAnalysis(Long videoId) {
        Video video = findVideo(videoId);
        return VideoAnalysisResponse.of(videoId, video.getStatus(),
                analysisMapper.findByVideoId(videoId));
    }

    @Override
    @Transactional
    public VideoAnalysisResponse ingestResult(Long videoId, AnalysisIngestRequest request) {
        findVideo(videoId);
        String status = request.status();
        if (!STATUS_DONE.equals(status) && !STATUS_FAILED.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "분석 상태는 done 또는 failed여야 합니다.");
        }
        // 재분석 멱등성 — 기존 결과를 비우고 새로 채운다.
        analysisMapper.deleteByVideoId(videoId);
        if (STATUS_DONE.equals(status)) {
            List<AnalysisResult> results = toResults(videoId, request);
            if (!results.isEmpty()) {
                analysisMapper.insertResults(results);
            }
        }
        videoMapper.updateStatus(videoId, status);
        return getAnalysis(videoId);
    }

    @Override
    @Transactional
    public VideoAnalysisResponse retryAnalysis(Long userId, Long videoId) {
        Video video = findVideo(videoId);
        if (!video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        analysisMapper.deleteByVideoId(videoId);
        videoMapper.updateStatus(videoId, STATUS_PENDING);
        analysisDispatcher.dispatch(videoId, video.getGcsPath());
        return getAnalysis(videoId);
    }

    @Override
    @Transactional
    public FeedbackResponse submitFeedback(Long userId, Long videoId, AnalysisFeedbackRequest request) {
        findVideo(videoId);
        // 라벨로 누적할 기술 — 올바르면 AI 라벨 그대로, 틀렸으면 사용자가 정정한 라벨.
        String technique = request.isCorrect() || request.correctLabel() == null
                ? request.techniqueLabel()
                : request.correctLabel();
        Label label = Label.builder()
                .videoId(videoId)
                .userId(userId)
                .technique(technique)
                .isCorrect(request.isCorrect())
                .build();
        analysisMapper.insertLabel(label);
        return new FeedbackResponse(label.getId());
    }

    private Video findVideo(Long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video;
    }

    private List<AnalysisResult> toResults(Long videoId, AnalysisIngestRequest request) {
        if (request.segments() == null) {
            return List.of();
        }
        return request.segments().stream()
                .map(s -> AnalysisResult.builder()
                        .videoId(videoId)
                        .sequenceIndex(s.sequenceIndex())
                        .startTimeMs(s.startTimeMs())
                        .endTimeMs(s.endTimeMs())
                        .technique(s.technique())
                        .isDynamic(s.isDynamic())
                        .confidence(s.confidence())
                        .modelVersion(request.modelVersion())
                        .build())
                .toList();
    }
}
