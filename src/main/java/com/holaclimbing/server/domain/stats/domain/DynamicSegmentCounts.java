package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 올린 영상의 영상 단위 대표 분석 결과 중 dynamic / static 개수.
 * final_is_dynamic 컬럼 기준 집계 결과.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DynamicSegmentCounts {

    private long dynamicCount;
    private long staticCount;

    /** dynamic 영상 수가 static보다 많으면 true (동률·없음은 false). */
    public boolean isDynamic() {
        return dynamicCount > staticCount;
    }

    public static DynamicSegmentCounts empty() {
        return new DynamicSegmentCounts(0L, 0L);
    }
}
