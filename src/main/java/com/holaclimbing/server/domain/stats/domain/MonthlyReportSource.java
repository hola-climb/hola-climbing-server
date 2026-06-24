package com.holaclimbing.server.domain.stats.domain;

public enum MonthlyReportSource {
    LOG("log"),
    VIDEO_FALLBACK("videoFallback"),
    NONE("none");

    private final String value;

    MonthlyReportSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
