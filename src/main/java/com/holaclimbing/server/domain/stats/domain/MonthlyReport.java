package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReport {

    private Long id;
    private Long userId;
    private String period;
    private Long selectedGymId;
    private String status;
    private String source;
    private String metrics;
    private String grade;
    private String tip;
    private String nextMonthGoal;
    private String recommendedGyms;
    private String narrative;
    private String requirement;
    private String model;
    private String promptVersion;
    private OffsetDateTime generatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
