package com.holaclimbing.server.domain.admin.dto.response;

import com.holaclimbing.server.domain.admin.domain.AdminAuditLog;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long id,
        Long adminId,
        String action,
        String targetType,
        Long targetId,
        String reason,
        String beforeJson,
        String afterJson,
        LocalDateTime createdAt
) {

    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAdminId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getReason(),
                log.getBeforeJson(),
                log.getAfterJson(),
                log.getCreatedAt());
    }
}
