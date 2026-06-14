package com.holaclimbing.server.domain.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.analysis.domain.AnalysisResult;
import com.holaclimbing.server.domain.analysis.domain.AnalysisTechniqueCatalog;
import com.holaclimbing.server.domain.analysis.domain.AnalysisVideoResult;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisFeedbackRequest;
import com.holaclimbing.server.domain.analysis.dto.request.AnalysisIngestRequest;
import com.holaclimbing.server.domain.analysis.dto.response.AnalysisModelMetricsResponse;
import com.holaclimbing.server.domain.analysis.dto.response.AnalysisTechniqueMetricResponse;
import com.holaclimbing.server.domain.analysis.dto.response.FeedbackResponse;
import com.holaclimbing.server.domain.analysis.dto.response.VideoAnalysisResponse;
import com.holaclimbing.server.domain.analysis.mapper.AnalysisMapper;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.mapper.VideoMapper;
import com.holaclimbing.server.domain.video.service.VideoAccessPolicy;
import com.holaclimbing.server.infrastructure.ai.AnalysisDispatcher;
import com.holaclimbing.server.infrastructure.ai.AnalysisProgress;
import com.holaclimbing.server.infrastructure.ai.AnalysisProgressBus;
import com.holaclimbing.server.infrastructure.ai.AnalysisStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_FAILED = "failed";

    private final AnalysisMapper analysisMapper;
    private final VideoMapper videoMapper;
    private final AnalysisDispatcher analysisDispatcher;
    private final AnalysisProgressBus progressBus;
    private final VideoAccessPolicy videoAccessPolicy;
    private final ObjectMapper objectMapper;

    @Override
    public VideoAnalysisResponse getAnalysis(Long videoId, Long viewerId) {
        Video video = findVideo(videoId);
        videoAccessPolicy.requireViewable(video, viewerId);
        return toResponse(video);
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
            analysisMapper.upsertVideoResult(toVideoResult(videoId, request, results));
        } else {
            analysisMapper.deleteVideoResultByVideoId(videoId);
        }
        videoMapper.updateStatus(videoId, status);
        // SSE·상태 저장소가 종료 이벤트를 보도록 동일 채널로 게시.
        progressBus.publish(AnalysisProgress.of(videoId,
                STATUS_DONE.equals(status) ? AnalysisStage.COMPLETED : AnalysisStage.FAILED,
                STATUS_DONE.equals(status) ? "분석 완료" : "분석 실패"));
        return toResponse(findVideo(videoId));
    }

    @Override
    @Transactional
    public VideoAnalysisResponse retryAnalysis(Long userId, Long videoId) {
        Video video = findVideo(videoId);
        if (!video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        analysisMapper.deleteByVideoId(videoId);
        analysisMapper.deleteVideoResultByVideoId(videoId);
        videoMapper.updateStatus(videoId, STATUS_PENDING);
        analysisDispatcher.dispatch(videoId, video.getGcsPath());
        return toResponse(findVideo(videoId));
    }

    @Override
    @Transactional
    public FeedbackResponse submitFeedback(Long userId, Long videoId, AnalysisFeedbackRequest request) {
        Video video = findVideo(videoId);
        if (!video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        AnalysisVideoResult result = analysisMapper.findVideoResultByVideoId(videoId);
        if (result == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "분석 완료 결과가 있는 영상만 피드백할 수 있습니다.");
        }
        List<String> finalTechniques = AnalysisTechniqueCatalog.normalizeTechniques(request.techniques());
        analysisMapper.updateFinalResultFromFeedback(
                videoId,
                toJson(finalTechniques),
                request.isDynamic(),
                request.note(),
                userId
        );
        return new FeedbackResponse(videoId);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisModelMetricsResponse getModelMetrics(String modelVersion) {
        List<AnalysisVideoResult> results = analysisMapper.findFeedbackAppliedByModelVersion(modelVersion);
        long feedbackCount = results.size();

        long dynamicEvaluatedCount = 0;
        long dynamicCorrectCount = 0;
        long exactTechniqueMatchCount = 0;

        Map<String, TechniqueCounter> counters = new LinkedHashMap<>();
        for (String technique : AnalysisTechniqueCatalog.CANONICAL_TECHNIQUES) {
            counters.put(technique, new TechniqueCounter());
        }

        for (AnalysisVideoResult result : results) {
            Set<String> aiTechniques = Set.copyOf(parseTechniqueJson(result.getAiTechniques()));
            Set<String> finalTechniques = Set.copyOf(parseTechniqueJson(result.getFinalTechniques()));
            if (aiTechniques.equals(finalTechniques)) {
                exactTechniqueMatchCount++;
            }
            if (result.getAiIsDynamic() != null && result.getFinalIsDynamic() != null) {
                dynamicEvaluatedCount++;
                if (result.getAiIsDynamic().equals(result.getFinalIsDynamic())) {
                    dynamicCorrectCount++;
                }
            }
            for (String technique : AnalysisTechniqueCatalog.CANONICAL_TECHNIQUES) {
                counters.get(technique).accept(aiTechniques.contains(technique), finalTechniques.contains(technique));
            }
        }

        Map<String, AnalysisTechniqueMetricResponse> perTechnique = new LinkedHashMap<>();
        for (Map.Entry<String, TechniqueCounter> entry : counters.entrySet()) {
            perTechnique.put(entry.getKey(), entry.getValue().toResponse(feedbackCount));
        }

        return new AnalysisModelMetricsResponse(
                modelVersion,
                feedbackCount,
                dynamicEvaluatedCount,
                ratio(dynamicCorrectCount, dynamicEvaluatedCount),
                ratio(exactTechniqueMatchCount, feedbackCount),
                perTechnique
        );
    }

    private Video findVideo(Long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video;
    }

    private VideoAnalysisResponse toResponse(Video video) {
        AnalysisVideoResult result = analysisMapper.findVideoResultByVideoId(video.getId());
        if (result == null) {
            return new VideoAnalysisResponse(video.getId(), video.getStatus(), null, List.of(), null, null, false);
        }
        return new VideoAnalysisResponse(
                video.getId(),
                video.getStatus(),
                result.getModelVersion(),
                parseTechniqueJson(result.getFinalTechniques()),
                result.getFinalIsDynamic(),
                result.getAiDynamicProbability(),
                result.isFeedbackApplied()
        );
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
                        .technique(AnalysisTechniqueCatalog.normalizeTechnique(s.technique()))
                        .isDynamic(s.isDynamic())
                        .confidence(s.confidence())
                        .modelVersion(request.modelVersion())
                        .build())
                .toList();
    }

    private AnalysisVideoResult toVideoResult(Long videoId, AnalysisIngestRequest request, List<AnalysisResult> results) {
        List<String> techniques = AnalysisTechniqueCatalog.normalizeTechniques(request.techniques());
        if (techniques.isEmpty()) {
            techniques = AnalysisTechniqueCatalog.normalizeTechniques(
                    results.stream()
                            .map(AnalysisResult::getTechnique)
                            .toList()
            );
        }
        String techniquesJson = toJson(techniques);
        return AnalysisVideoResult.builder()
                .videoId(videoId)
                .modelVersion(request.modelVersion())
                .aiTechniques(techniquesJson)
                .aiIsDynamic(request.isDynamic())
                .aiDynamicProbability(request.dynamicProbability())
                .finalTechniques(techniquesJson)
                .finalIsDynamic(request.isDynamic())
                .feedbackApplied(false)
                .build();
    }

    private String toJson(List<String> techniques) {
        try {
            return objectMapper.writeValueAsString(techniques);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "분석 결과 직렬화에 실패했습니다.");
        }
    }

    private List<String> parseTechniqueJson(String techniquesJson) {
        if (techniquesJson == null || techniquesJson.isBlank()) {
            return List.of();
        }
        try {
            return AnalysisTechniqueCatalog.normalizeTechniques(
                    objectMapper.readValue(techniquesJson, new TypeReference<List<String>>() {
                    })
            );
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "분석 결과 파싱에 실패했습니다.");
        }
    }

    private Double ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return (double) numerator / denominator;
    }

    private static class TechniqueCounter {

        private long truePositive;
        private long falsePositive;
        private long falseNegative;
        private long trueNegative;

        private void accept(boolean predicted, boolean actual) {
            if (predicted && actual) {
                truePositive++;
                return;
            }
            if (predicted) {
                falsePositive++;
                return;
            }
            if (actual) {
                falseNegative++;
                return;
            }
            trueNegative++;
        }

        private AnalysisTechniqueMetricResponse toResponse(long total) {
            Double precision = safeRatio(truePositive, truePositive + falsePositive);
            Double recall = safeRatio(truePositive, truePositive + falseNegative);
            Double f1 = precision == null || recall == null || precision + recall == 0
                    ? null
                    : 2 * precision * recall / (precision + recall);
            return new AnalysisTechniqueMetricResponse(
                    truePositive,
                    falsePositive,
                    falseNegative,
                    trueNegative,
                    safeRatio(truePositive + trueNegative, total),
                    precision,
                    recall,
                    f1
            );
        }

        private Double safeRatio(long numerator, long denominator) {
            if (denominator == 0) {
                return null;
            }
            return (double) numerator / denominator;
        }
    }
}
