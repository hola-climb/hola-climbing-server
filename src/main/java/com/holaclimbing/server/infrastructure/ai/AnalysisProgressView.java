package com.holaclimbing.server.infrastructure.ai;

import java.time.Instant;

/**
 * 클라이언트에 노출하는 분석 진행 상태 view.
 * 내부 진행 단계(AnalysisStage)를 명세의 status/progress/stage 형태로 변환한다.
 */
public record AnalysisProgressView(
        Long videoId,
        String status,
        int progress,
        String stage,
        String message,
        Instant updatedAt,
        Integer estimatedSecondsRemaining
) {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ANALYZING = "analyzing";
    private static final String STATUS_DONE = "done";
    private static final String STATUS_FAILED = "failed";

    public static AnalysisProgressView from(AnalysisProgress progress) {
        return new AnalysisProgressView(
                progress.videoId(),
                statusOf(progress.stage()),
                progressOf(progress),
                stageOf(progress),
                progress.message(),
                progress.updatedAt(),
                null
        );
    }

    public static AnalysisProgressView from(Long videoId, String videoStatus, AnalysisProgress progress) {
        if (STATUS_DONE.equals(videoStatus)) {
            return terminal(videoId, STATUS_DONE, "completed", "분석 완료");
        }
        if (STATUS_FAILED.equals(videoStatus)) {
            return terminal(videoId, STATUS_FAILED, "failed", "분석 실패");
        }
        if (progress != null) {
            return from(progress);
        }
        return new AnalysisProgressView(videoId, STATUS_PENDING, 0, "queued", null, null, null);
    }

    private static AnalysisProgressView terminal(Long videoId, String status, String stage, String message) {
        return new AnalysisProgressView(videoId, status, 100, stage, message, null, null);
    }

    private static String statusOf(AnalysisStage stage) {
        return switch (stage) {
            case QUEUED -> STATUS_PENDING;
            case PROCESSING -> STATUS_ANALYZING;
            case COMPLETED -> STATUS_DONE;
            case FAILED -> STATUS_FAILED;
        };
    }

    private static int progressOf(AnalysisProgress progress) {
        return switch (progress.stage()) {
            case QUEUED -> 0;
            case COMPLETED, FAILED -> 100;
            case PROCESSING -> processingProgress(progress.message());
        };
    }

    private static int processingProgress(String message) {
        if ("분석 시작".equals(message)) {
            return 10;
        }
        if ("영상 다운로드 완료".equals(message)) {
            return 30;
        }
        if ("포즈 추정 완료".equals(message)) {
            return 70;
        }
        if ("기술 분류 완료, 결과 전송 중".equals(message)) {
            return 90;
        }
        return 50;
    }

    private static String stageOf(AnalysisProgress progress) {
        return switch (progress.stage()) {
            case QUEUED -> "queued";
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case PROCESSING -> processingStage(progress.message());
        };
    }

    private static String processingStage(String message) {
        if ("분석 시작".equals(message)) {
            return "started";
        }
        if ("영상 다운로드 완료".equals(message)) {
            return "downloaded";
        }
        if ("포즈 추정 완료".equals(message)) {
            return "pose_estimation";
        }
        if ("기술 분류 완료, 결과 전송 중".equals(message)) {
            return "classification";
        }
        return "processing";
    }
}
