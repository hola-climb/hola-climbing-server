package com.holaclimbing.server.domain.analysis.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 분석 피드백 라벨. labels 테이블 매핑.
 * 사용자가 AI 추론을 '맞아요/틀렸어요'로 평가한 결과 — 재학습 데이터로 누적된다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Label {

    private Long id;
    private Long videoId;
    private Long userId;
    private String technique;
    private Boolean isCorrect;
    private LocalDateTime createdAt;
}
