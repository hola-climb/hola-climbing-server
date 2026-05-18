package com.holaclimbing.server.domain.notification.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import com.holaclimbing.server.domain.notification.dto.response.NotificationResponse;
import com.holaclimbing.server.domain.notification.dto.response.NotificationSettingsResponse;

public interface NotificationService {

    /** 영상에 댓글이 달렸을 때 영상 소유자에게 알림. */
    void notifyComment(Long videoOwnerId, Long commenterId, Long videoId);

    /** 댓글에 답글이 달렸을 때 부모 댓글 작성자에게 알림. */
    void notifyReply(Long parentAuthorId, Long replierId, Long replyCommentId);

    /** 영상에 좋아요가 눌렸을 때 영상 소유자에게 알림. */
    void notifyLike(Long videoOwnerId, Long likerId, Long videoId);

    /** 팔로우 당했을 때 대상에게 알림. */
    void notifyFollow(Long followedId, Long followerId);

    /** 내 알림 목록 조회. */
    PageResponse<NotificationResponse> getNotifications(Long userId, boolean unreadOnly, int page, int size);

    /** 미읽음 알림 개수. */
    long getUnreadCount(Long userId);

    /** 단건 읽음 처리. 처리 후 남은 미읽음 개수를 반환. */
    long markRead(Long userId, Long notificationId);

    /** 모든 알림 읽음 처리. 처리 후 남은 미읽음 개수(0)를 반환. */
    long markAllRead(Long userId);

    /** 알림 삭제. */
    void deleteNotification(Long userId, Long notificationId);

    /** 알림 설정 조회. 설정 행이 없으면 기본값(전부 ON). */
    NotificationSettingsResponse getSettings(Long userId);

    /** 알림 설정 부분 변경. */
    NotificationSettingsResponse updateSettings(Long userId, UpdateNotificationSettingsRequest request);
}
