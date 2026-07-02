package com.holaclimbing.server.domain.video.mapper;

import com.holaclimbing.server.domain.video.domain.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface VideoMapper {

    /** 영상 저장. 생성된 PK는 video.id로 채워진다. */
    void insert(Video video);

    /** 영상 단건 조회 (soft-delete 제외). 없으면 null. */
    Video findById(Long id);

    /** 사용자에게 노출 가능한 영상 단건 조회 (soft-delete, 비활성 작성자/암장 제외). */
    Video findVisibleById(Long id);

    /**
     * 커서 기반 영상 피드. 공개 영상과 viewer 본인의 비공개 영상을 촬영일 최신순으로 keyset 스캔.
     * cursorRecordedDate/cursorId가 null이면 첫 페이지, 아니면 해당 촬영일+id 뒤부터.
     * recordedDate가 있으면 해당 촬영일의 영상만 필터.
     * hasNext 판정을 위해 limit는 보통 size+1로 넘긴다.
     */
    List<Video> findFeedByCursor(@Param("userId") Long userId,
                                 @Param("cursorRecordedDate") LocalDate cursorRecordedDate,
                                 @Param("cursorId") Long cursorId,
                                 @Param("recordedDate") LocalDate recordedDate,
                                 @Param("limit") int limit,
                                 @Param("viewerId") Long viewerId);

    /** 특정 암장의 영상 목록. gymGradeId가 있으면 난이도 필터 적용. */
    List<Video> findByGym(@Param("gymId") Long gymId,
                          @Param("gymGradeId") Long gymGradeId,
                          @Param("size") int size,
                          @Param("offset") int offset,
                          @Param("viewerId") Long viewerId);

    /** findByGym 결과 총 개수. gymGradeId가 있으면 난이도 필터 적용. */
    long countByGym(@Param("gymId") Long gymId,
                    @Param("gymGradeId") Long gymGradeId,
                    @Param("viewerId") Long viewerId);

    int incrementViewCount(Long id);

    int incrementLikeCount(Long id);

    int decrementLikeCount(Long id);

    int incrementCommentCount(Long id);

    int decrementCommentCount(Long id);

    /** 영상 부분 수정 — null이 아닌 필드만 갱신. */
    int updateVideo(@Param("id") Long id,
                    @Param("title") String title,
                    @Param("description") String description,
                    @Param("isPublic") Boolean isPublic,
                    @Param("gymId") Long gymId,
                    @Param("gymGradeId") Long gymGradeId,
                    @Param("recordedDate") LocalDate recordedDate);

    /** 영상 soft-delete. */
    int softDelete(Long id);

    /** 분석 상태 갱신 (pending/done/failed). */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
