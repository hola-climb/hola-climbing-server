package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportAggregate {

    private int sessions;
    private int videos;
    private int analyzedVideos;
    private int problemsSolved;
    private int gymsVisited;
    private Long primaryGymId;
    private String primaryGymName;
    private int dynamicCount;
    private int staticCount;
    private Long gradeGymId;
    private String gradeGymName;
    private String maxGrade;
    private Integer maxGradeOrder;
    private MonthlyReportSource source;

    public boolean hasSessions() {
        return sessions > 0;
    }

    public MonthlyReportAggregate withSource(MonthlyReportSource newSource) {
        this.source = newSource;
        return this;
    }

    public Map<String, Object> toPromptMap() {
        return Map.of(
                "sessions", sessions,
                "videos", videos,
                "analyzedVideos", analyzedVideos,
                "problemsSolved", problemsSolved,
                "gymsVisited", gymsVisited,
                "primaryGymName", primaryGymName == null ? "" : primaryGymName,
                "dynamicCount", dynamicCount,
                "staticCount", staticCount
        );
    }
}
