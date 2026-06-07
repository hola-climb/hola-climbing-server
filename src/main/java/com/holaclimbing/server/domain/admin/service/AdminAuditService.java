package com.holaclimbing.server.domain.admin.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminAuditLogResponse;

public interface AdminAuditService {

    void record(Long adminId, String action, String targetType, Long targetId,
                String reason, Object before, Object after);

    PageResponse<AdminAuditLogResponse> search(String targetType, Long targetId, Long adminId, int page, int size);
}
