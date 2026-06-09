package com.holaclimbing.server.infrastructure.ai;

import com.holaclimbing.server.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 분석 인프라 — Redis Streams 작업 큐, Pub/Sub 진행 버스, 상태 저장소의 통합 동작 검증.
 */
@SpringBootTest(properties = "app.cors.allowed-origins=http://localhost:3000")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AnalysisInfraIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private AnalysisJobQueue jobQueue;

    @Autowired
    private AnalysisProgressBus progressBus;

    @Autowired
    private AnalysisStatusStore statusStore;

    @Autowired
    private VideoAnalysisSseService sseService;

    @Autowired
    private AnalysisDeadLetterQueue deadLetterQueue;

    @Test
    @DisplayName("DLQ — 처리 불가능한 진행 이벤트는 dead-letter에 적재된다")
    void malformedProgress_landsInDeadLetterQueue() {
        long before = deadLetterQueue.size();

        // 역직렬화 불가능한 raw 메시지를 progress 채널에 직접 발행 → listener 처리 실패 → DLQ.
        redis.convertAndSend(RedisAnalysisProgressBus.CHANNEL, "{ this is not valid json");

        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(deadLetterQueue.size()).isGreaterThan(before));
    }

    @Test
    @DisplayName("작업 큐 — enqueue하면 Redis Stream에 적재된다")
    void enqueue_appendsRecordToStream() {
        jobQueue.enqueue(new AnalysisJob(10001L, "videos/10001.mp4", "http://localhost/cb"));

        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .range(RedisStreamAnalysisJobQueue.STREAM_KEY, Range.unbounded());
        assertThat(records).isNotEmpty();
        assertThat(records.get(records.size() - 1).getValue())
                .containsEntry("videoId", "10001")
                .containsEntry("gcsPath", "videos/10001.mp4");
    }

    @Test
    @DisplayName("작업 큐 — gcsPath가 비어 있으면 Stream에 적재하지 않는다")
    void enqueue_whenGcsPathBlank_rejectsRecord() {
        assertThatThrownBy(() -> jobQueue.enqueue(new AnalysisJob(10002L, " ", "http://localhost/cb")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gcsPath");
    }

    @Test
    @DisplayName("진행 버스 — publish하면 listener가 상태 저장소를 갱신한다")
    void progressPublish_updatesStatusStore() {
        Long videoId = 20002L;
        progressBus.publish(AnalysisProgress.of(videoId, AnalysisStage.PROCESSING, "분석 중"));

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<AnalysisProgress> found = statusStore.find(videoId);
            assertThat(found).isPresent();
            assertThat(found.get().stage()).isEqualTo(AnalysisStage.PROCESSING);
        });
    }

    @Test
    @DisplayName("진행 이벤트 — AI 워커 snake_case JSON을 그대로 수신해 상태 저장소를 갱신한다")
    void workerSnakeCaseProgress_updatesStatusStore() {
        Long videoId = 50005L;
        redis.convertAndSend(RedisAnalysisProgressBus.CHANNEL, """
                {
                  "video_id": 50005,
                  "stage": "PROCESSING",
                  "message": "프레임 추출 중",
                  "updated_at": "2026-05-28T10:32:45.123Z"
                }
                """);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<AnalysisProgress> found = statusStore.find(videoId);
            assertThat(found).isPresent();
            assertThat(found.get().videoId()).isEqualTo(videoId);
            assertThat(found.get().stage()).isEqualTo(AnalysisStage.PROCESSING);
            assertThat(found.get().updatedAt()).isEqualTo(java.time.Instant.parse("2026-05-28T10:32:45.123Z"));
        });
    }

    @Test
    @DisplayName("SSE 서비스 — connect/broadcast가 예외 없이 동작하고 비종료 이벤트는 구독자 유지")
    void sseBroadcast_keepsSubscriberForNonTerminalEvents() {
        Long videoId = 30003L;
        SseEmitter emitter = sseService.connect(videoId);
        assertThat(sseService.subscriberCount(videoId)).isEqualTo(1);

        // 비종료 단계는 emitter를 닫지 않으므로 구독자 수가 유지된다.
        sseService.broadcast(AnalysisProgress.of(videoId, AnalysisStage.PROCESSING, "중간"));
        assertThat(sseService.subscriberCount(videoId)).isEqualTo(1);
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("상태 저장소 — 저장한 진행 상태를 그대로 조회할 수 있다")
    void statusStore_roundtrip() {
        AnalysisProgress saved = AnalysisProgress.of(40004L, AnalysisStage.QUEUED, "대기");
        statusStore.save(saved);

        Optional<AnalysisProgress> loaded = statusStore.find(40004L);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().videoId()).isEqualTo(40004L);
        assertThat(loaded.get().stage()).isEqualTo(AnalysisStage.QUEUED);
        assertThat(loaded.get().message()).isEqualTo("대기");
    }
}
