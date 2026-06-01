package com.holaclimbing.server.domain.video.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LikeMapper {

    /** 좋아요 등록(중복 안전). 신규로 들어가면 1, 이미 있어 무시되면 0을 반환한다. */
    int insert(@Param("userId") Long userId, @Param("videoId") Long videoId);

    int delete(@Param("userId") Long userId, @Param("videoId") Long videoId);

    boolean exists(@Param("userId") Long userId, @Param("videoId") Long videoId);
}
