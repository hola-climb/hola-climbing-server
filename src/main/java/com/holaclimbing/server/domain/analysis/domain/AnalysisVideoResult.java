package com.holaclimbing.server.domain.analysis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 영상 단위 대표 분석 결과. 세그먼트 raw 결과와 분리해 클라이언트 표시·피드백·모델 통계에 사용한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisVideoResult {

    private Long videoId;
    private String modelVersion;
    private String aiTechniques;
    private Boolean aiIsDynamic;
    private Float aiDynamicProbability;
    private String finalTechniques;
    private Boolean finalIsDynamic;
    private boolean feedbackApplied;
    private String feedbackNote;
    private Long correctedBy;
    private LocalDateTime correctedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
