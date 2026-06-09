package com.holaclimbing.server.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 영상 등록 시 AI 워커(Python)에 분석을 요청하는 디스패처.
 *
 * <p>이전에는 HTTP POST로 직접 호출했으나, 운영 안정성과 다중 인스턴스 fan-out을 위해
 * Redis Streams 기반 작업 큐로 전환했다. 큐 적재 직후 상태 저장소에 QUEUED 상태를
 * 기록해 SSE·폴링이 즉시 초기 상태를 볼 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisDispatcher {

    private final AnalysisJobQueue jobQueue;
    private final AnalysisStatusStore statusStore;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * 분석 요청을 큐에 적재하고 상태를 QUEUED로 표기한다.
     *
     * <p>호출자가 트랜잭션 내부면 실제 큐 적재는 AFTER_COMMIT으로 미룬다.
     * 메인 트랜잭션이 롤백되면 분석도 디스패치되지 않으며, Redis 통신 시간이 영상 등록
     * 트랜잭션을 길게 만들지 않는다.</p>
     */
    public void dispatch(Long videoId, String gcsPath) {
        requireGcsPath(gcsPath);
        String callbackUrl = baseUrl + "/api/analysis/videos/" + videoId;
        Runnable task = () -> {
            try {
                jobQueue.enqueue(new AnalysisJob(videoId, gcsPath, callbackUrl));
                statusStore.save(AnalysisProgress.of(videoId, AnalysisStage.QUEUED, "분석 대기열에 등록됨"));
            } catch (Exception e) {
                log.warn("AI 분석 디스패치 실패 — videoId={}: {}", videoId, e.getMessage());
                statusStore.save(AnalysisProgress.of(videoId, AnalysisStage.FAILED,
                        "분석 디스패치에 실패했습니다. 잠시 후 다시 시도해 주세요."));
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private static void requireGcsPath(String gcsPath) {
        if (!StringUtils.hasText(gcsPath)) {
            throw new IllegalArgumentException("gcsPath must not be blank");
        }
    }
}
