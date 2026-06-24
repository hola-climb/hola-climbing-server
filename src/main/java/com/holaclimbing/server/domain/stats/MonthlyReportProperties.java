package com.holaclimbing.server.domain.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.monthly-report")
public record MonthlyReportProperties(
        int minVideos,
        int minProblems,
        boolean generateOnMiss,
        String promptVersion,
        Scheduler scheduler,
        Llm llm
) {
    public record Scheduler(
            boolean enabled,
            String cron
    ) {
    }

    public record Llm(
            String mode,
            String baseUrl,
            String apiKey,
            String model,
            int timeoutSeconds
    ) {
    }
}
