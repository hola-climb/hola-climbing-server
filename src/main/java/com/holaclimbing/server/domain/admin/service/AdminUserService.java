package com.holaclimbing.server.domain.admin.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.dto.request.AdminReasonRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminUserRoleRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminUserStatusRequest;
import com.holaclimbing.server.domain.admin.dto.response.AdminUserDetailResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminUserSearchResponse;

public interface AdminUserService {

    PageResponse<AdminUserSearchResponse> search(String status, String role, String keyword,
                                                 Boolean emailVerified, int page, int size);

    AdminUserDetailResponse getUser(Long userId);

    AdminUserDetailResponse changeStatus(Long adminId, Long userId, AdminUserStatusRequest request);

    AdminUserDetailResponse changeRole(Long adminId, Long userId, AdminUserRoleRequest request);

    AdminUserDetailResponse revokeTokens(Long adminId, Long userId, AdminReasonRequest request);
}
