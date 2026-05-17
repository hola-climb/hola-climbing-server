package com.holaclimbing.server.domain.user.mapper;

import com.holaclimbing.server.domain.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBlockMapper {

    void insert(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    int delete(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    boolean exists(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    long countBlocked(Long blockerId);

    /** blockerId가 차단한 사용자 목록 (최신순). */
    List<User> findBlocked(@Param("blockerId") Long blockerId,
                           @Param("size") int size,
                           @Param("offset") int offset);
}
