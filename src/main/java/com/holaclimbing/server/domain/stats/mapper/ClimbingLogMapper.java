package com.holaclimbing.server.domain.stats.mapper;

import com.holaclimbing.server.domain.stats.domain.CalendarVideoStats;
import com.holaclimbing.server.domain.stats.domain.ClimbingLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ClimbingLogMapper {

    /** 기록 저장. 생성된 PK는 log.id로 채워진다. */
    void insert(ClimbingLog log);

    /** 기록 단건 조회 (삭제되지 않은 것만). 없으면 null. */
    ClimbingLog findById(Long id);

    /** 특정 사용자의 climbed_on 기간 내 기록 (최신순). */
    List<ClimbingLog> findByUserAndPeriod(@Param("userId") Long userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /** 특정 사용자의 recorded_date 기간 내 영상 일별 집계. */
    List<CalendarVideoStats> findVideoStatsByUserAndPeriod(@Param("userId") Long userId,
                                                           @Param("from") LocalDate from,
                                                           @Param("to") LocalDate to);

    /** 특정 사용자의 특정 날짜 기록 (최신순). */
    List<ClimbingLog> findByUserAndDate(@Param("userId") Long userId,
                                        @Param("date") LocalDate date);

    /** 기록 수정. */
    int update(@Param("id") Long id,
               @Param("gymId") Long gymId,
               @Param("climbedOn") LocalDate climbedOn,
               @Param("gradeCounts") String gradeCounts,
               @Param("memo") String memo);

    /** 기록 소프트 삭제 (deleted_at 설정). */
    int softDelete(Long id);
}
