package com.holaclimbing.server.domain.stats.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MonthlyReportResponse(
        String period,
        String status,
        String source,
        OffsetDateTime generatedAt,
        Metrics metrics,
        Grade grade,
        Tip tip,
        Goal nextMonthGoal,
        List<RecommendedGym> recommendedGyms,
        Narrative narrative,
        Requirement requirement
) {
    public record Metrics(
            int sessions,
            int videos,
            int problemsSolved,
            int gymsVisited,
            Long primaryGymId,
            String primaryGymName,
            int dynamicCount,
            int staticCount,
            Double dynamicRatio,
            Double staticRatio,
            Map<String, Integer> techniqueCounts
    ) {
    }

    public record Grade(
            Long gymId,
            String gymName,
            String maxGrade,
            String maxGradePrevMonth
    ) {
    }

    public record Tip(
            String type,
            List<String> techniqueKeys,
            String message
    ) {
    }

    public record Goal(
            String title,
            String metric,
            int target,
            List<String> techniqueKeys,
            String rationale
    ) {
    }

    public record RecommendedGym(
            Long gymId,
            String name,
            List<String> matchedTechniqueKeys,
            int matchingVideoCount,
            String reason
    ) {
    }

    public record Narrative(
            String headline,
            String summary,
            List<String> highlights
    ) {
    }

    public record Requirement(
            int minVideos,
            int minProblems
    ) {
    }
}
