package com.holaclimbing.server.domain.video.mapper;

import com.holaclimbing.server.domain.video.domain.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VideoMapper {

    /** 영상 저장. 생성된 PK는 video.id로 채워진다. */
    void insert(Video video);

    /** 영상 단건 조회 (soft-delete 제외). 없으면 null. */
    Video findById(Long id);

    /** 공개 피드 — is_public이고 삭제/실패 상태가 아닌 영상 (최신순). userId가 있으면 해당 업로더로 필터. */
    List<Video> findFeed(@Param("userId") Long userId,
                         @Param("size") int size,
                         @Param("offset") int offset);

    /** findFeed 결과 총 개수. */
    long countFeed(@Param("userId") Long userId);

    /** 특정 암장의 공개 영상 (최신순). */
    List<Video> findByGym(@Param("gymId") Long gymId,
                          @Param("size") int size,
                          @Param("offset") int offset);

    /** findByGym 결과 총 개수. */
    long countByGym(Long gymId);

    int incrementViewCount(Long id);

    int incrementLikeCount(Long id);

    int decrementLikeCount(Long id);

    int incrementCommentCount(Long id);

    int decrementCommentCount(Long id);

    /** 영상 부분 수정 — null이 아닌 필드만 갱신. */
    int updateVideo(@Param("id") Long id,
                    @Param("title") String title,
                    @Param("description") String description,
                    @Param("grade") String grade,
                    @Param("isPublic") Boolean isPublic);

    /** 영상 soft-delete. */
    int softDelete(Long id);

    /** 분석 상태 갱신 (pending/analyzing/done/failed). */
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
