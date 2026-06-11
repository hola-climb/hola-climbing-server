package com.holaclimbing.server.domain.stats.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CalendarVideoStats {

    private LocalDate date;
    private int videoCount;
    private long totalDurationSeconds;
}
