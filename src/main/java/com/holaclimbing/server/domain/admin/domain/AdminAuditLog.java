package com.holaclimbing.server.domain.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {

    private Long id;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private String beforeJson;
    private String afterJson;
    private LocalDateTime createdAt;
}
