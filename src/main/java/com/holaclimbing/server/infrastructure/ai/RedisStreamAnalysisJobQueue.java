package com.holaclimbing.server.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis Streams 기반 분석 요청 큐. Python 워커는 consumer group으로 `analysis:requests`를
 * XREADGROUP하여 ack 후 처리한다 (재처리·dead-letter 가능).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamAnalysisJobQueue implements AnalysisJobQueue {

    public static final String STREAM_KEY = "analysis:requests";

    private final StringRedisTemplate redis;

    @Override
    public void enqueue(AnalysisJob job) {
        Map<String, String> payload = new HashMap<>();
        payload.put("videoId", String.valueOf(job.videoId()));
        payload.put("gcsPath", job.gcsPath() == null ? "" : job.gcsPath());
        payload.put("callbackUrl", job.callbackUrl() == null ? "" : job.callbackUrl());
        redis.opsForStream().add(MapRecord.create(STREAM_KEY, payload));
        log.info("분석 요청 큐 적재 — videoId={}", job.videoId());
    }
}
