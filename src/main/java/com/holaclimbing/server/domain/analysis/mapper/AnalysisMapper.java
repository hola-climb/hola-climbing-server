package com.holaclimbing.server.domain.analysis.mapper;

import com.holaclimbing.server.domain.analysis.domain.AnalysisResult;
import com.holaclimbing.server.domain.analysis.domain.Label;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalysisMapper {

    /** 분석 결과 세그먼트 일괄 저장. */
    void insertResults(@Param("results") List<AnalysisResult> results);

    /** 분석 피드백 라벨 저장. 생성된 PK는 label.id로 채워진다. */
    void insertLabel(Label label);

    /** 영상의 분석 결과 세그먼트 목록 (sequence_index 순). */
    List<AnalysisResult> findByVideoId(Long videoId);

    /** 영상의 기존 분석 결과 전체 삭제 (재분석 시 멱등성 확보). */
    void deleteByVideoId(Long videoId);
}
