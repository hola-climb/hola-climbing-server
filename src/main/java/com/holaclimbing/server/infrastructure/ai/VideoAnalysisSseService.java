package com.holaclimbing.server.infrastructure.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 영상별 SSE Emitter 레지스트리. 진행 이벤트를 해당 video의 모든 구독자에게 fan-out한다.
 * 단일 인스턴스 내 emitter만 관리하며, 다중 인스턴스 간 fan-out은 Redis Pub/Sub이 담당한다.
 */
@Slf4j
@Service
public class VideoAnalysisSseService {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** videoId 채널에 새 SSE 연결을 등록한다. */
    public SseEmitter connect(Long videoId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        emitters.computeIfAbsent(videoId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(videoId, emitter));
        emitter.onTimeout(() -> remove(videoId, emitter));
        emitter.onError(t -> remove(videoId, emitter));
        return emitter;
    }

    /** 진행 이벤트를 해당 video의 모든 구독자에게 보낸다. */
    public void broadcast(AnalysisProgress progress) {
        Set<SseEmitter> set = emitters.get(progress.videoId());
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter em : set) {
            try {
                em.send(SseEmitter.event().name("progress").data(AnalysisProgressView.from(progress)));
                if (progress.stage() == AnalysisStage.COMPLETED || progress.stage() == AnalysisStage.FAILED) {
                    em.complete();
                }
            } catch (IOException e) {
                log.debug("SSE emitter 전송 실패 — 제거 — videoId={}", progress.videoId());
                remove(progress.videoId(), em);
            }
        }
    }

    /** 현재 구독자 수 (테스트·관측용). */
    public int subscriberCount(Long videoId) {
        Set<SseEmitter> set = emitters.get(videoId);
        return set == null ? 0 : set.size();
    }

    private void remove(Long videoId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(videoId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(videoId);
            }
        }
    }
}
