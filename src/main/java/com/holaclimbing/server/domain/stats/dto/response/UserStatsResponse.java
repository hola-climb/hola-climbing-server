package com.holaclimbing.server.domain.stats.dto.response;

import com.holaclimbing.server.domain.stats.domain.DynamicSegmentCounts;
import com.holaclimbing.server.domain.stats.domain.Stats;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 사용자 클라이밍 통계 응답.
 *
 * <ul>
 *   <li>{@code techniqueCounts}: 동작별 누적 횟수 (예: {@code {"highstep": 12, "flagging": 8}}).</li>
 *   <li>{@code dynamicCount}/{@code staticCount}: 사용자가 올린 영상의 대표 분석 결과 중
 *       동적/정적 영상 개수. 분석이 없으면 둘 다 0.</li>
 *   <li>{@code isDynamic}: dynamicCount &gt; staticCount면 true (다이나믹한 사람).
 *       동률·데이터 없음은 false.</li>
 * </ul>
 */
public record UserStatsResponse(
        Long userId,
        int totalVideos,
        long totalClimbingSeconds,
        Map<String, Integer> techniqueCounts,
        long dynamicCount,
        long staticCount,
        boolean isDynamic,
        OffsetDateTime lastClimbedAt
) {
    public static UserStatsResponse of(Stats stats,
                                       Map<String, Integer> techniqueCounts,
                                       DynamicSegmentCounts dynamicCounts) {
        DynamicSegmentCounts dc = dynamicCounts == null ? DynamicSegmentCounts.empty() : dynamicCounts;
        return new UserStatsResponse(
                stats.getUserId(),
                stats.getTotalVideos(),
                stats.getTotalClimbingSeconds(),
                techniqueCounts,
                dc.getDynamicCount(),
                dc.getStaticCount(),
                dc.isDynamic(),
                stats.getLastClimbedAt()
        );
    }

    /** 아직 분석 데이터가 없는 사용자 — 0으로 채운 통계. */
    public static UserStatsResponse empty(Long userId, DynamicSegmentCounts dynamicCounts) {
        DynamicSegmentCounts dc = dynamicCounts == null ? DynamicSegmentCounts.empty() : dynamicCounts;
        return new UserStatsResponse(userId, 0, 0L, Map.of(),
                dc.getDynamicCount(), dc.getStaticCount(), dc.isDynamic(), null);
    }
}
