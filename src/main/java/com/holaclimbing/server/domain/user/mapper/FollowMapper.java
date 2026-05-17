package com.holaclimbing.server.domain.user.mapper;

import com.holaclimbing.server.domain.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FollowMapper {

    void insert(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    int delete(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    boolean exists(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    /** userId를 팔로우하는 사람 수. */
    long countFollowers(Long userId);

    /** userId가 팔로우하는 사람 수. */
    long countFollowing(Long userId);

    /** userId를 팔로우하는 사용자 목록 (최신순). */
    List<User> findFollowers(@Param("userId") Long userId,
                             @Param("size") int size,
                             @Param("offset") int offset);

    /** userId가 팔로우하는 사용자 목록 (최신순). */
    List<User> findFollowing(@Param("userId") Long userId,
                             @Param("size") int size,
                             @Param("offset") int offset);
}
