package com.holaclimbing.server.domain.stats.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportNarrative {

    private String headline;
    private String summary;
    private List<String> highlights;
}
