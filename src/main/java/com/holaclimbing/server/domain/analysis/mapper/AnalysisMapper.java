package com.holaclimbing.server.domain.analysis.mapper;

import com.holaclimbing.server.domain.analysis.domain.AnalysisResult;
import com.holaclimbing.server.domain.analysis.domain.AnalysisVideoResult;
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

    /** 영상 단위 대표 분석 결과 저장. 재분석 시 ai/final 결과를 새 추론으로 초기화한다. */
    void upsertVideoResult(AnalysisVideoResult result);

    /** 영상 단위 대표 분석 결과 조회. */
    AnalysisVideoResult findVideoResultByVideoId(Long videoId);

    /** 영상 단위 대표 분석 결과 삭제. */
    void deleteVideoResultByVideoId(Long videoId);

    /** 사용자 피드백을 최종 결과로 반영하되 AI 원본 결과는 보존한다. */
    void updateFinalResultFromFeedback(@Param("videoId") Long videoId,
                                       @Param("finalTechniques") String finalTechniques,
                                       @Param("finalIsDynamic") Boolean finalIsDynamic,
                                       @Param("feedbackNote") String feedbackNote,
                                       @Param("correctedBy") Long correctedBy);

    /** 특정 모델 버전의 피드백 반영 결과 목록. 모델 정확도 통계 산출용. */
    List<AnalysisVideoResult> findFeedbackAppliedByModelVersion(@Param("modelVersion") String modelVersion);

    /** 영상의 분석 결과 세그먼트 목록 (sequence_index 순). */
    List<AnalysisResult> findByVideoId(Long videoId);

    /** 영상의 기존 분석 결과 전체 삭제 (재분석 시 멱등성 확보). */
    void deleteByVideoId(Long videoId);
}
