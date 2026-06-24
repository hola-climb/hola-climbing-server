package com.holaclimbing.server.common.config;

import com.holaclimbing.server.domain.stats.MonthlyReportProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MonthlyReportProperties.class)
public class MonthlyReportConfig {
}
