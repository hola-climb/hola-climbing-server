package com.holaclimbing.server.domain.stats.dto.response;

import java.util.List;

public record MonthlyReportAvailablePeriodsResponse(
        List<String> periods
) {
}
