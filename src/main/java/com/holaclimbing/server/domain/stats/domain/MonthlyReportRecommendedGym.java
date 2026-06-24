package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportRecommendedGym {

    private Long gymId;
    private String name;
    private int matchingVideoCount;
    private String matchedTechniques;
}
