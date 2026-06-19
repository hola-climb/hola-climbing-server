package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 사용자 클라이밍 통계 조회용 projection.
 * 영상/대표 분석 결과를 집계한 techniqueCounts JSON을 raw 문자열로 읽어 서비스에서 파싱한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Stats {

    private Long userId;
    private int totalVideos;
    private long totalClimbingSeconds;
    private String techniqueCounts;
    private OffsetDateTime lastClimbedAt;
    private OffsetDateTime updatedAt;
}
