package com.holaclimbing.server.domain.stats.dto.response;

import com.holaclimbing.server.domain.stats.domain.ClimbingLog;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 클라이밍 기록 응답. totalProblems는 난이도별 푼 문제 수의 합.
 */
public record ClimbingLogResponse(
        Long id,
        Long userId,
        Long gymId,
        LocalDate climbedOn,
        Map<String, Integer> gradeCounts,
        int totalProblems,
        String memo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ClimbingLogResponse of(ClimbingLog log, Map<String, Integer> gradeCounts) {
        int total = gradeCounts.values().stream().filter(v -> v != null).mapToInt(Integer::intValue).sum();
        return new ClimbingLogResponse(
                log.getId(), log.getUserId(), log.getGymId(), log.getClimbedOn(),
                gradeCounts, total, log.getMemo(), log.getCreatedAt(), log.getUpdatedAt());
    }
}
