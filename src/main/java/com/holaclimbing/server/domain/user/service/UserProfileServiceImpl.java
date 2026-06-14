package com.holaclimbing.server.domain.user.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.common.upload.ImageUploadValidator;
import com.holaclimbing.server.common.upload.ImageUploadValidator.ImageUpload;
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
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final long MAX_PROFILE_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final String PROFILE_IMAGE_PREFIX = "profile-images";

    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final UserBlockMapper userBlockMapper;
    private final DeviceTokenMapper deviceTokenMapper;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final UserTokenRevoker userTokenRevoker;
    private final GcsStorageService gcsStorageService;

    @Override
    public MyProfileResponse getMyProfile(Long userId) {
        User user = findActiveUser(userId);
        return MyProfileResponse.of(user,
                followMapper.countFollowers(userId), followMapper.countFollowing(userId),
                resolveProfileImage(user.getProfileImage()));
    }

    @Override
    @Transactional
    public MyProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())
                && userMapper.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        if (request.profileImage() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "프로필 이미지는 multipart endpoint로 업로드해야 합니다.");
        }
        userMapper.updateProfile(userId, request.nickname(), null, request.bio());

        User updated = findActiveUser(userId);
        return MyProfileResponse.of(updated,
                followMapper.countFollowers(userId), followMapper.countFollowing(userId),
                resolveProfileImage(updated.getProfileImage()));
    }

    @Override
    @Transactional
    public MyProfileResponse uploadProfileImage(Long userId, MultipartFile image) {
        findActiveUser(userId);
        ImageUpload upload = ImageUploadValidator.validate(image, "프로필 이미지", MAX_PROFILE_IMAGE_BYTES);
        String objectPath = PROFILE_IMAGE_PREFIX + "/" + userId + "/" + UUID.randomUUID() + "." + upload.extension();
        try {
            gcsStorageService.uploadBytes(objectPath, upload.contentType(), image.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.GCS_UPLOAD_FAILED);
        }
        userMapper.updateProfile(userId, null, objectPath, null);

        User updated = findActiveUser(userId);
        return MyProfileResponse.of(updated,
                followMapper.countFollowers(userId), followMapper.countFollowing(userId),
                resolveProfileImage(updated.getProfileImage()));
    }

    @Override
    public UserProfileResponse getUserProfile(Long targetUserId, Long viewerId) {
        User user = findActiveUser(targetUserId);
        boolean isFollowing = viewerId != null && followMapper.exists(viewerId, targetUserId);
        return UserProfileResponse.of(user,
                followMapper.countFollowers(targetUserId), followMapper.countFollowing(targetUserId),
                isFollowing, resolveProfileImage(user.getProfileImage()));
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
                .stream().map(user -> UserSummaryResponse.from(user, resolveProfileImage(user.getProfileImage())))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public PageResponse<UserSummaryResponse> getFollowing(Long userId, int page, int size) {
        findActiveUser(userId);
        long total = followMapper.countFollowing(userId);
        List<UserSummaryResponse> content = followMapper.findFollowing(userId, size, page * size)
                .stream().map(user -> UserSummaryResponse.from(user, resolveProfileImage(user.getProfileImage())))
                .toList();
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
                .stream().map(user -> UserSummaryResponse.from(user, resolveProfileImage(user.getProfileImage())))
                .toList();
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

    private String resolveProfileImage(String storedProfileImage) {
        if (storedProfileImage == null || storedProfileImage.isBlank()) {
            return null;
        }
        if (storedProfileImage.startsWith("http://") || storedProfileImage.startsWith("https://")) {
            return storedProfileImage;
        }
        return gcsStorageService.createReadUrl(storedProfileImage);
    }
}
