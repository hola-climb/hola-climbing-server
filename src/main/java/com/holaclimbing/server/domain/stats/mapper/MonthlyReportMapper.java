package com.holaclimbing.server.domain.stats.mapper;

import com.holaclimbing.server.domain.stats.domain.MonthlyReport;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportRecommendedGym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface MonthlyReportMapper {
    MonthlyReport findReport(@Param("userId") Long userId,
                             @Param("period") String period,
                             @Param("gymId") Long gymId);

    List<String> findAvailablePeriods(@Param("userId") Long userId);

    void upsertReport(MonthlyReport report);

    MonthlyReportAggregate findLogAggregate(@Param("userId") Long userId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    MonthlyReportAggregate findVideoFallbackAggregate(@Param("userId") Long userId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    String findTechniqueCountsJson(@Param("userId") Long userId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    MonthlyReportAggregate findDynamicStaticAggregate(@Param("userId") Long userId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    MonthlyReportAggregate findGradeFromLogs(@Param("userId") Long userId,
                                             @Param("gymId") Long gymId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    MonthlyReportAggregate findGradeFromVideos(@Param("userId") Long userId,
                                               @Param("gymId") Long gymId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    List<MonthlyReportRecommendedGym> findRecommendedGymsByTechniques(
            @Param("techniques") List<String> techniques,
            @Param("size") int size);
}
