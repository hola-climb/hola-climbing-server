package com.holaclimbing.server.domain.user.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.dto.request.UpdateProfileRequest;
import com.holaclimbing.server.domain.user.dto.response.MyProfileResponse;
import com.holaclimbing.server.domain.user.dto.response.UserProfileResponse;
import com.holaclimbing.server.domain.user.dto.response.UserSummaryResponse;
import com.holaclimbing.server.domain.notification.service.NotificationService;
import com.holaclimbing.server.common.security.UserTokenRevoker;
import com.holaclimbing.server.domain.user.mapper.DeviceTokenMapper;
import com.holaclimbing.server.domain.user.mapper.FollowMapper;
import com.holaclimbing.server.domain.user.mapper.UserBlockMapper;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final UserBlockMapper userBlockMapper;
    private final DeviceTokenMapper deviceTokenMapper;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final UserTokenRevoker userTokenRevoker;

    @Override
    public MyProfileResponse getMyProfile(Long userId) {
        User user = findActiveUser(userId);
        return MyProfileResponse.of(user,
                followMapper.countFollowers(userId), followMapper.countFollowing(userId));
    }

    @Override
    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())
                && userMapper.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        userMapper.updateProfile(userId, request.nickname(), request.profileImage(), request.bio());

        User updated = findActiveUser(userId);
        return MyProfileResponse.of(updated,
                followMapper.countFollowers(userId), followMapper.countFollowing(userId));
    }

    @Override
    public UserProfileResponse getUserProfile(Long targetUserId, Long viewerId) {
        User user = findActiveUser(targetUserId);
        boolean isFollowing = viewerId != null && followMapper.exists(viewerId, targetUserId);
        return UserProfileResponse.of(user,
                followMapper.countFollowers(targetUserId), followMapper.countFollowing(targetUserId),
                isFollowing);
    }

    @Override
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신을 팔로우할 수 없습니다.");
        }
        findActiveUser(followingId);
        if (followMapper.exists(followerId, followingId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 팔로우한 사용자입니다.");
        }
        followMapper.insert(followerId, followingId);
        notificationService.notifyFollow(followingId, followerId);
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        followMapper.delete(followerId, followingId);
    }

    @Override
    public PageResponse<UserSummaryResponse> getFollowers(Long userId, int page, int size) {
        findActiveUser(userId);
        long total = followMapper.countFollowers(userId);
        List<UserSummaryResponse> content = followMapper.findFollowers(userId, size, page * size)
                .stream().map(UserSummaryResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public PageResponse<UserSummaryResponse> getFollowing(Long userId, int page, int size) {
        findActiveUser(userId);
        long total = followMapper.countFollowing(userId);
        List<UserSummaryResponse> content = followMapper.findFollowing(userId, size, page * size)
                .stream().map(UserSummaryResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신을 차단할 수 없습니다.");
        }
        findActiveUser(blockedId);
        if (userBlockMapper.exists(blockerId, blockedId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 차단한 사용자입니다.");
        }
        userBlockMapper.insert(blockerId, blockedId);
        followMapper.delete(blockerId, blockedId);
        followMapper.delete(blockedId, blockerId);
    }

    @Override
    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        userBlockMapper.delete(blockerId, blockedId);
    }

    @Override
    public PageResponse<UserSummaryResponse> getBlockedUsers(Long blockerId, int page, int size) {
        long total = userBlockMapper.countBlocked(blockerId);
        List<UserSummaryResponse> content = userBlockMapper.findBlocked(blockerId, size, page * size)
                .stream().map(UserSummaryResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public void withdraw(Long userId, String password) {
        User user = findActiveUser(userId);
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        // FCM 푸시 차단 — 탈퇴 후에도 디바이스 토큰이 남아 있으면 알림이 계속 도달한다.
        deviceTokenMapper.deleteByUserId(userId);
        // 활성 access/refresh 토큰을 사용자 단위로 즉시 무효화.
        userTokenRevoker.revokeAllFor(userId);
        // PII 익명화 — UNIQUE(email)/UNIQUE(nickname) 제약 해제로 동일 이메일/닉네임 재가입 허용.
        userMapper.anonymize(userId,
                "deleted_" + userId + "@removed.local",
                "deleted_" + userId);
        userMapper.softDelete(userId);
    }

    private User findActiveUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
