package com.holaclimbing.server.domain.notification;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.notification.dto.request.UpdateNotificationSettingsRequest;
import com.holaclimbing.server.domain.notification.dto.response.NotificationResponse;
import com.holaclimbing.server.domain.notification.dto.response.NotificationSettingsResponse;
import com.holaclimbing.server.domain.notification.dto.response.UnreadCountResponse;
import com.holaclimbing.server.domain.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 API. 모두 인증이 필요한 본인 전용 엔드포인트.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private static final String READ_ALL = "all";

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        return ApiResponse.success(notificationService.getNotifications(userId, unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(notificationService.getUnreadCount(userId));
    }

    /** id가 "all"이면 전체 읽음, 아니면 해당 알림만 읽음 처리한다. */
    @PatchMapping("/{id}/read")
    public ApiResponse<UnreadCountResponse> markRead(@AuthenticationPrincipal Long userId,
                                                     @PathVariable String id) {
        long unreadCount = READ_ALL.equals(id)
                ? notificationService.markAllRead(userId)
                : notificationService.markRead(userId, parseId(id));
        return ApiResponse.success(new UnreadCountResponse(unreadCount));
    }

    @GetMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> getSettings(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(notificationService.getSettings(userId));
    }

    @PatchMapping("/settings")
    public ApiResponse<NotificationSettingsResponse> updateSettings(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return ApiResponse.success(notificationService.updateSettings(userId, request));
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> deleteNotification(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long notificationId) {
        notificationService.deleteNotification(userId, notificationId);
        return ApiResponse.success();
    }

    private static Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 알림 식별자입니다.");
        }
    }
}
