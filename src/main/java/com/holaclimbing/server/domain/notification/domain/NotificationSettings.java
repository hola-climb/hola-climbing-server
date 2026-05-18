package com.holaclimbing.server.domain.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 종류별 수신 설정. user_notification_settings 테이블 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings {

    private Long userId;
    private Boolean notifyComment;
    private Boolean notifyReply;
    private Boolean notifyLike;
    private Boolean notifyFollow;
    private Boolean notifyChat;
    private Boolean notifySystem;
}
