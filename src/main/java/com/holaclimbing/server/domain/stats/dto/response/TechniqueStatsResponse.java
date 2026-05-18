package com.holaclimbing.server.domain.stats.dto.response;

import java.util.Map;

/**
 * 기술별 사용 통계 응답 (F-03-01).
 * techniqueCounts: 동작별 누적 횟수, mostUsed/leastUsed: 최다/최소 사용 기술 (데이터 없으면 null).
 */
public record TechniqueStatsResponse(
        Map<String, Integer> techniqueCounts,
        String mostUsed,
        String leastUsed
) {
}
