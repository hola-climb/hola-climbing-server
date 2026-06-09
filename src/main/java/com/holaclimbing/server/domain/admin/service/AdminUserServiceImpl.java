package com.holaclimbing.server.domain.admin.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.common.security.UserTokenRevoker;
import com.holaclimbing.server.domain.admin.dto.request.AdminReasonRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminUserRoleRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminUserStatusRequest;
import com.holaclimbing.server.domain.admin.dto.response.AdminUserDetailResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminUserSearchResponse;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.mapper.DeviceTokenMapper;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "SUSPENDED", "DELETED");
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final UserMapper userMapper;
    private final DeviceTokenMapper deviceTokenMapper;
    private final UserTokenRevoker userTokenRevoker;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserSearchResponse> search(String status, String role, String keyword,
                                                        Boolean emailVerified, int page, int size) {
        String normalizedStatus = normalizeBlankToNull(status);
        String normalizedRole = normalizeBlankToNull(role);
        long total = userMapper.countAdminUsers(normalizedStatus, normalizedRole, keyword, emailVerified);
        var content = userMapper.searchAdminUsers(normalizedStatus, normalizedRole, keyword,
                        emailVerified, size, page * size)
                .stream()
                .map(AdminUserSearchResponse::from)
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(Long userId) {
        return AdminUserDetailResponse.from(requireUser(userId));
    }

    @Override
    @Transactional
    public AdminUserDetailResponse changeStatus(Long adminId, Long userId, AdminUserStatusRequest request) {
        if (adminId != null && adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신의 상태는 변경할 수 없습니다.");
        }
        String status = normalizeStatus(request.status());
        User before = requireUser(userId);

        userMapper.updateStatus(userId, status);
        if (!"ACTIVE".equals(status)) {
            revokeActiveSessions(userId);
        }

        User after = requireUser(userId);
        adminAuditService.record(adminId, "USER_STATUS_CHANGE", "user", userId, request.reason(), before, after);
        return AdminUserDetailResponse.from(after);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse changeRole(Long adminId, Long userId, AdminUserRoleRequest request) {
        if (adminId != null && adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "자기 자신의 역할은 변경할 수 없습니다.");
        }
        String role = normalizeRole(request.role());
        User before = requireUser(userId);

        if (role.equals(before.getRole())) {
            return AdminUserDetailResponse.from(before);
        }

        validateLastActiveAdminNotRemoved(before, role);

        userMapper.updateRole(userId, role);
        revokeActiveSessions(userId);

        User after = requireUser(userId);
        adminAuditService.record(adminId, "USER_ROLE_CHANGE", "user", userId, request.reason(), before, after);
        return AdminUserDetailResponse.from(after);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse revokeTokens(Long adminId, Long userId, AdminReasonRequest request) {
        User user = requireUser(userId);
        revokeActiveSessions(userId);
        adminAuditService.record(adminId, "USER_TOKEN_REVOKE", "user", userId, request.reason(), user, user);
        return AdminUserDetailResponse.from(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void validateLastActiveAdminNotRemoved(User target, String nextRole) {
        if (!"ADMIN".equals(target.getRole()) || !"ACTIVE".equals(target.getStatus()) || !"USER".equals(nextRole)) {
            return;
        }
        List<Long> activeAdminIds = userMapper.lockActiveAdminIds();
        boolean anotherActiveAdminExists = activeAdminIds.stream()
                .anyMatch(adminId -> !adminId.equals(target.getId()));
        if (!anotherActiveAdminExists) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "마지막 활성 관리자는 강등할 수 없습니다.");
        }
    }

    private void revokeActiveSessions(Long userId) {
        userTokenRevoker.revokeAllFor(userId);
        deviceTokenMapper.deleteByUserId(userId);
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeBlankToNull(status);
        if (normalized == null || !ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 회원 상태입니다.");
        }
        return normalized;
    }

    private String normalizeRole(String role) {
        String normalized = normalizeBlankToNull(role);
        if (normalized == null || !ALLOWED_ROLES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 회원 역할입니다.");
        }
        return normalized;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
