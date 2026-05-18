package com.holaclimbing.server.domain.notification.dto.request;

/**
 * 알림 설정 변경 요청. null인 필드는 기존 값을 유지한다 (부분 수정).
 */
public record UpdateNotificationSettingsRequest(
        Boolean notifyComment,
        Boolean notifyReply,
        Boolean notifyLike,
        Boolean notifyFollow,
        Boolean notifyChat,
        Boolean notifySystem
) {
}
