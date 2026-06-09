package com.holaclimbing.server.infrastructure.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * AnalysisDispatcher 단위 테스트 — 큐 적재와 상태 저장이 함께 일어나는지,
 * 그리고 큐 실패가 호출자에 전파되지 않는지(fire-and-forget) 검증.
 */
class AnalysisDispatcherTest {

    private List<AnalysisJob> enqueued;
    private Map<Long, AnalysisProgress> saved;
    private AnalysisDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        enqueued = new ArrayList<>();
        saved = new HashMap<>();

        AnalysisJobQueue queue = enqueued::add;
        AnalysisStatusStore store = new AnalysisStatusStore(null, null) {
            @Override
            public void save(AnalysisProgress progress) {
                saved.put(progress.videoId(), progress);
            }

            @Override
            public Optional<AnalysisProgress> find(Long videoId) {
                return Optional.ofNullable(saved.get(videoId));
            }
        };
        dispatcher = new AnalysisDispatcher(queue, store);
        ReflectionTestUtils.setField(dispatcher, "baseUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("dispatch — 큐에 적재하고 상태를 QUEUED로 기록한다")
    void dispatch_enqueuesAndMarksQueued() {
        dispatcher.dispatch(42L, "videos/uploads/42/clip.mp4");

        assertThat(enqueued).hasSize(1);
        assertThat(enqueued.get(0).videoId()).isEqualTo(42L);
        assertThat(enqueued.get(0).callbackUrl()).isEqualTo("http://localhost:8080/api/analysis/videos/42");
        assertThat(saved.get(42L).stage()).isEqualTo(AnalysisStage.QUEUED);
    }

    @Test
    @DisplayName("큐 적재 실패 시에도 예외를 삼키고 FAILED 상태를 저장한다")
    void dispatch_whenQueueFails_marksFailedAndSwallowsError() {
        AnalysisJobQueue failingQueue = job -> {
            throw new RuntimeException("redis down");
        };
        AnalysisDispatcher failing = new AnalysisDispatcher(failingQueue, new AnalysisStatusStore(null, null) {
            @Override
            public void save(AnalysisProgress progress) {
                saved.put(progress.videoId(), progress);
            }
        });
        ReflectionTestUtils.setField(failing, "baseUrl", "http://localhost:8080");

        assertThatNoException()
                .isThrownBy(() -> failing.dispatch(1L, "videos/uploads/1/clip.mp4"));
        assertThat(saved.get(1L).stage()).isEqualTo(AnalysisStage.FAILED);
        assertThat(saved.get(1L).message()).contains("디스패치");
    }
}
