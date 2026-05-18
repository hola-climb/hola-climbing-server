package com.holaclimbing.server.domain.notification.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.notification.domain.Notification;
import com.holaclimbing.server.domain.notification.domain.NotificationSettings;
import com.holaclimbing.server.domain.notification.domain.NotificationType;
import com.holaclimbing.server.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import com.holaclimbing.server.domain.notification.dto.response.NotificationResponse;
import com.holaclimbing.server.domain.notification.dto.response.NotificationSettingsResponse;
import com.holaclimbing.server.domain.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void notifyComment(Long videoOwnerId, Long commenterId, Long videoId) {
        create(videoOwnerId, commenterId, NotificationType.COMMENT, "video", videoId);
    }

    @Override
    public void notifyReply(Long parentAuthorId, Long replierId, Long replyCommentId) {
        create(parentAuthorId, replierId, NotificationType.REPLY, "comment", replyCommentId);
    }

    @Override
    public void notifyLike(Long videoOwnerId, Long likerId, Long videoId) {
        create(videoOwnerId, likerId, NotificationType.LIKE, "video", videoId);
    }

    @Override
    public void notifyFollow(Long followedId, Long followerId) {
        create(followedId, followerId, NotificationType.FOLLOW, "user", followerId);
    }

    @Override
    public PageResponse<NotificationResponse> getNotifications(Long userId, boolean unreadOnly,
                                                               int page, int size) {
        long total = notificationMapper.countByRecipient(userId, unreadOnly);
        List<NotificationResponse> content = notificationMapper
                .findByRecipient(userId, unreadOnly, size, page * size)
                .stream().map(NotificationResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    @Transactional
    public long markRead(Long userId, Long notificationId) {
        if (notificationMapper.markRead(notificationId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        return notificationMapper.countUnread(userId);
    }

    @Override
    @Transactional
    public long markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
        return notificationMapper.countUnread(userId);
    }

    @Override
    public NotificationSettingsResponse getSettings(Long userId) {
        NotificationSettings settings = notificationMapper.findSettings(userId);
        return settings != null
                ? NotificationSettingsResponse.of(settings)
                : NotificationSettingsResponse.defaults();
    }

    @Override
    @Transactional
    public NotificationSettingsResponse updateSettings(Long userId,
                                                       UpdateNotificationSettingsRequest request) {
        NotificationSettingsResponse current = getSettings(userId);
        boolean comment = request.notifyComment() != null ? request.notifyComment() : current.notifyComment();
        boolean reply = request.notifyReply() != null ? request.notifyReply() : current.notifyReply();
        boolean like = request.notifyLike() != null ? request.notifyLike() : current.notifyLike();
        boolean follow = request.notifyFollow() != null ? request.notifyFollow() : current.notifyFollow();
        boolean chat = request.notifyChat() != null ? request.notifyChat() : current.notifyChat();
        boolean system = request.notifySystem() != null ? request.notifySystem() : current.notifySystem();
        notificationMapper.upsertSettings(userId, comment, reply, like, follow, chat, system);
        return new NotificationSettingsResponse(comment, reply, like, follow, chat, system);
    }

    @Override
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        if (notificationMapper.delete(notificationId, userId) == 0) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    /** 알림 생성. 자기 자신이 행위자인 경우(recipient == sender)에는 알림을 만들지 않는다. */
    private void create(Long recipientId, Long senderId, NotificationType type,
                        String targetType, Long targetId) {
        if (recipientId.equals(senderId)) {
            return;
        }
        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .senderId(senderId)
                .type(type.getCode())
                .targetType(targetType)
                .targetId(targetId)
                .title(type.getTitle())
                .content(type.getDefaultContent())
                .build();
        notificationMapper.insert(notification);
    }
}
