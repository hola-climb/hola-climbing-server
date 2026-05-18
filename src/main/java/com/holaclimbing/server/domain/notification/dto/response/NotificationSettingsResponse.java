package com.holaclimbing.server.domain.notification.dto.response;

import com.holaclimbing.server.domain.notification.domain.NotificationSettings;

/**
 * 알림 설정 응답. 종류별 ON/OFF 토글.
 */
public record NotificationSettingsResponse(
        boolean notifyComment,
        boolean notifyReply,
        boolean notifyLike,
        boolean notifyFollow,
        boolean notifyChat,
        boolean notifySystem
) {
    public static NotificationSettingsResponse of(NotificationSettings s) {
        return new NotificationSettingsResponse(
                s.getNotifyComment(), s.getNotifyReply(), s.getNotifyLike(),
                s.getNotifyFollow(), s.getNotifyChat(), s.getNotifySystem());
    }

    /** 설정 행이 없는 사용자 — 전부 ON. */
    public static NotificationSettingsResponse defaults() {
        return new NotificationSettingsResponse(true, true, true, true, true, true);
    }
}
