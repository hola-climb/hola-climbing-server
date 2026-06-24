package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;

import java.util.List;
import java.util.Map;

public interface MonthlyReportNarrativeClient {

    MonthlyReportNarrative generate(MonthlyReportAggregate aggregate,
                                    Map<String, Integer> techniqueCounts,
                                    List<String> underusedTechniques);
}
