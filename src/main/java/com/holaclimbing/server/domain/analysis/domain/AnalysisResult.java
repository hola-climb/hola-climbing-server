package com.holaclimbing.server.domain.analysis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 영상 분석 결과 한 세그먼트. analysis_results 테이블 매핑.
 * 한 영상은 sequenceIndex로 구분되는 여러 세그먼트 결과를 가진다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    private Long id;
    private Long videoId;
    private int sequenceIndex;
    private Integer startTimeMs;
    private Integer endTimeMs;
    private String technique;
    private Boolean isDynamic;
    private Float confidence;
    private String modelVersion;
    private OffsetDateTime createdAt;
}
