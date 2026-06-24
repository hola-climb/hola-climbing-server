package com.holaclimbing.server.domain.stats.domain;

public enum MonthlyReportStatus {
    READY("ready"),
    INSUFFICIENT_DATA("insufficientData"),
    GENERATING("generating"),
    FAILED("failed");

    private final String value;

    MonthlyReportStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
