package com.holaclimbing.server.domain.user.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.user.dto.request.UpdateProfileRequest;
import com.holaclimbing.server.domain.user.dto.response.MyProfileResponse;
import com.holaclimbing.server.domain.user.dto.response.UserProfileResponse;
import com.holaclimbing.server.domain.user.dto.response.UserSummaryResponse;

public interface UserProfileService {

    /** 내 프로필 조회. */
    MyProfileResponse getMyProfile(Long userId);

    /** 내 프로필 부분 수정. */
    MyProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request);

    /** 다른 사용자 프로필 조회. viewerId가 있으면 팔로우 여부를 함께 반환. */
    UserProfileResponse getUserProfile(Long targetUserId, Long viewerId);

    /** 팔로우. */
    void follow(Long followerId, Long followingId);

    /** 언팔로우. */
    void unfollow(Long followerId, Long followingId);

    /** userId를 팔로우하는 사용자 목록. */
    PageResponse<UserSummaryResponse> getFollowers(Long userId, int page, int size);

    /** userId가 팔로우하는 사용자 목록. */
    PageResponse<UserSummaryResponse> getFollowing(Long userId, int page, int size);

    /** 차단. 차단 시 양방향 팔로우 관계도 함께 해제한다. */
    void block(Long blockerId, Long blockedId);

    /** 차단 해제. */
    void unblock(Long blockerId, Long blockedId);

    /** 내가 차단한 사용자 목록. */
    PageResponse<UserSummaryResponse> getBlockedUsers(Long blockerId, int page, int size);
}
