package com.holaclimbing.server.domain.admin;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminAuditLogResponse;
import com.holaclimbing.server.domain.admin.service.AdminAuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
public class AdminAuditLogController {

    private final AdminAuditService adminAuditService;

    @GetMapping("/api/admin/audit-logs")
    public ApiResponse<PageResponse<AdminAuditLogResponse>> search(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Long adminId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(adminAuditService.search(targetType, targetId, adminId, page, size));
    }
}
