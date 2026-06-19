package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 사용자가 직접 작성하는 클라이밍 기록. climbing_logs 테이블 매핑.
 * grade_counts(jsonb)는 String으로 읽어 서비스에서 파싱한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClimbingLog {

    private Long id;
    private Long userId;
    private Long gymId;
    private LocalDate climbedOn;
    private String gradeCounts;
    private String memo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
