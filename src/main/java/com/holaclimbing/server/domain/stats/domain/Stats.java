package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 클라이밍 통계 엔티티. user_stats 테이블 매핑.
 * AI 분석 파이프라인이 갱신하며, 서버는 조회만 한다.
 * techniqueCounts는 JSONB 컬럼을 raw JSON 문자열로 읽어 서비스에서 파싱한다.
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
    private LocalDateTime lastClimbedAt;
    private LocalDateTime updatedAt;
}
