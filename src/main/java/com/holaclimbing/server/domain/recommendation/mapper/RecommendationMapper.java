package com.holaclimbing.server.domain.recommendation.mapper;

import com.holaclimbing.server.domain.video.domain.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecommendationMapper {

    /** 추천 피드 — 공개 영상 (본인 제외), 팔로잉 업로더 우선 + 최신순. */
    List<Video> findFeedVideos(@Param("userId") Long userId,
                               @Param("size") int size,
                               @Param("offset") int offset);

    /** findFeedVideos 결과 총 개수. */
    long countFeedVideos(Long userId);

    /** 사용자가 팔로우하는 사용자 id 목록. */
    List<Long> findFollowingIds(Long userId);
}
