package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.dto.response.TechniqueStatsResponse;
import com.holaclimbing.server.domain.stats.dto.response.UserStatsResponse;

public interface StatsService {

    /** 사용자 클라이밍 통계 조회. 분석 데이터가 없으면 0으로 채운 통계를 반환한다. */
    UserStatsResponse getUserStats(Long userId);

    /** 기술별 사용 통계 조회 (최다/최소 사용 기술 포함). */
    TechniqueStatsResponse getTechniqueStats(Long userId);
}
