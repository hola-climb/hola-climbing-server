package com.holaclimbing.server.domain.admin;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.dto.request.AdminReasonRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminUserRoleRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminUserStatusRequest;
import com.holaclimbing.server.domain.admin.dto.response.AdminUserDetailResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminUserSearchResponse;
import com.holaclimbing.server.domain.admin.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<PageResponse<AdminUserSearchResponse>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean emailVerified,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(adminUserService.search(status, role, keyword, emailVerified, page, size));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable Long userId) {
        return ApiResponse.success(adminUserService.getUser(userId));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserDetailResponse> changeStatus(@AuthenticationPrincipal Long adminId,
                                                             @PathVariable Long userId,
                                                             @Valid @RequestBody AdminUserStatusRequest request) {
        return ApiResponse.success(adminUserService.changeStatus(adminId, userId, request));
    }

    @PatchMapping("/{userId}/role")
    public ApiResponse<AdminUserDetailResponse> changeRole(@AuthenticationPrincipal Long adminId,
                                                           @PathVariable Long userId,
                                                           @Valid @RequestBody AdminUserRoleRequest request) {
        return ApiResponse.success(adminUserService.changeRole(adminId, userId, request));
    }

    @PostMapping("/{userId}/revoke-tokens")
    public ApiResponse<AdminUserDetailResponse> revokeTokens(@AuthenticationPrincipal Long adminId,
                                                             @PathVariable Long userId,
                                                             @Valid @RequestBody AdminReasonRequest request) {
        return ApiResponse.success(adminUserService.revokeTokens(adminId, userId, request));
    }
}
