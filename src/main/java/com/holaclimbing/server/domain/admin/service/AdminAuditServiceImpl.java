package com.holaclimbing.server.domain.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.domain.AdminAuditLog;
import com.holaclimbing.server.domain.admin.dto.response.AdminAuditLogResponse;
import com.holaclimbing.server.domain.admin.mapper.AdminAuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

    private static final String EMPTY_JSON = "{}";

    private final AdminAuditLogMapper adminAuditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void record(Long adminId, String action, String targetType, Long targetId,
                       String reason, Object before, Object after) {
        adminAuditLogMapper.insert(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .reason(reason)
                .beforeJson(toJson(before))
                .afterJson(toJson(after))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogResponse> search(String targetType, Long targetId, Long adminId,
                                                      int page, int size) {
        int offset = page * size;
        var content = adminAuditLogMapper.search(targetType, targetId, adminId, size, offset).stream()
                .map(AdminAuditLogResponse::from)
                .toList();
        long total = adminAuditLogMapper.countSearch(targetType, targetId, adminId);
        return PageResponse.of(content, page, size, total);
    }

    private String toJson(Object value) {
        if (value == null) {
            return EMPTY_JSON;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("감사 로그 JSON 직렬화에 실패했습니다.", e);
        }
    }
}
