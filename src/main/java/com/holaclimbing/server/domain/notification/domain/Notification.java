package com.holaclimbing.server.domain.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 알림 엔티티. notifications 테이블 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private Long id;
    private Long recipientId;
    private Long senderId;
    private String type;
    private String targetType;
    private Long targetId;
    private String title;
    private String content;
    private boolean isRead;
    private OffsetDateTime createdAt;
}
