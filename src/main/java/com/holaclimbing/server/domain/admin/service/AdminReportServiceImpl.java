package com.holaclimbing.server.domain.admin.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.common.security.UserTokenRevoker;
import com.holaclimbing.server.domain.admin.dto.request.AdminReportStatusRequest;
import com.holaclimbing.server.domain.admin.dto.response.AdminReportResponse;
import com.holaclimbing.server.domain.admin.mapper.AdminReportMapper;
import com.holaclimbing.server.domain.report.domain.Report;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.mapper.DeviceTokenMapper;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.video.domain.Comment;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.mapper.CommentMapper;
import com.holaclimbing.server.domain.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("reviewed", "resolved", "rejected");
    private static final Set<String> ALLOWED_ACTIONS =
            Set.of("none", "delete_video", "delete_comment", "suspend_user");

    private final AdminReportMapper adminReportMapper;
    private final VideoMapper videoMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final DeviceTokenMapper deviceTokenMapper;
    private final UserTokenRevoker userTokenRevoker;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReportResponse> search(String status, String targetType, String category,
                                                    int page, int size) {
        long total = adminReportMapper.countSearch(status, targetType, category);
        var content = adminReportMapper.search(status, targetType, category, size, page * size)
                .stream()
                .map(AdminReportResponse::from)
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public AdminReportResponse changeStatus(Long adminId, Long reportId, AdminReportStatusRequest request) {
        String status = normalizeStatus(request.status());
        String action = normalizeAction(request.resolutionAction());
        Report before = requireReport(reportId);

        applyResolutionAction(adminId, before, action, request.reason());
        adminReportMapper.updateStatus(reportId, status, adminId);
        syncPendingReportsForHandledTarget(adminId, before, status, action, request.reason());

        Report after = requireReport(reportId);
        adminAuditService.record(adminId, "REPORT_STATUS_CHANGE", "report", reportId,
                request.reason(), before, after);
        return AdminReportResponse.from(after);
    }

    private void applyResolutionAction(Long adminId, Report report, String action, String reason) {
        switch (action) {
            case "none" -> {
            }
            case "delete_video" -> deleteVideo(adminId, report, reason);
            case "delete_comment" -> deleteComment(adminId, report, reason);
            case "suspend_user" -> suspendTargetUser(adminId, report, reason);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 신고 처리 액션입니다.");
        }
    }

    private void deleteVideo(Long adminId, Report report, String reason) {
        if (!"video".equals(report.getTargetType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "영상 신고에만 영상 삭제 액션을 적용할 수 있습니다.");
        }
        Video video = videoMapper.findById(report.getTargetId());
        if (video == null) {
            return;
        }
        videoMapper.softDelete(report.getTargetId());
        adminAuditService.record(adminId, "MODERATION_DELETE_VIDEO", "video", report.getTargetId(),
                reason, video, null);
    }

    private void deleteComment(Long adminId, Report report, String reason) {
        if (!"comment".equals(report.getTargetType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "댓글 신고에만 댓글 삭제 액션을 적용할 수 있습니다.");
        }
        Comment comment = commentMapper.findById(report.getTargetId());
        if (comment == null) {
            return;
        }
        commentMapper.softDelete(report.getTargetId());
        videoMapper.decrementCommentCount(comment.getVideoId());
        adminAuditService.record(adminId, "MODERATION_DELETE_COMMENT", "comment", report.getTargetId(),
                reason, comment, null);
    }

    private void suspendTargetUser(Long adminId, Report report, String reason) {
        Long targetUserId = resolveTargetUserId(report);
        User before = requireUser(targetUserId);
        userMapper.updateStatus(targetUserId, "SUSPENDED");
        userTokenRevoker.revokeAllFor(targetUserId);
        deviceTokenMapper.deleteByUserId(targetUserId);
        User after = requireUser(targetUserId);
        adminAuditService.record(adminId, "MODERATION_SUSPEND_USER", "user", targetUserId,
                reason, before, after);
    }

    private Long resolveTargetUserId(Report report) {
        if ("user".equals(report.getTargetType())) {
            return report.getTargetId();
        }
        if ("video".equals(report.getTargetType())) {
            Video video = videoMapper.findById(report.getTargetId());
            if (video == null) {
                throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
            }
            return video.getUserId();
        }
        if ("comment".equals(report.getTargetType())) {
            Comment comment = commentMapper.findById(report.getTargetId());
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
            }
            return comment.getUserId();
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "회원 정지 대상을 확인할 수 없습니다.");
    }

    private void syncPendingReportsForHandledTarget(Long adminId, Report report, String status, String action,
                                                    String reason) {
        if ("none".equals(action)) {
            return;
        }
        int synced = adminReportMapper.updatePendingByTarget(
                report.getTargetType(), report.getTargetId(), status, adminId);
        if (synced > 0) {
            adminAuditService.record(adminId, "REPORT_TARGET_STATUS_SYNC", report.getTargetType(), report.getTargetId(),
                    reason, null, "syncedPendingReports=" + synced + ", status=" + status);
        }
    }

    private Report requireReport(Long reportId) {
        Report report = adminReportMapper.findById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없습니다.");
        }
        return report;
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeBlankToNull(status);
        if (normalized == null || !ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 신고 상태입니다.");
        }
        return normalized;
    }

    private String normalizeAction(String action) {
        String normalized = normalizeBlankToNull(action);
        if (normalized == null || !ALLOWED_ACTIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 신고 처리 액션입니다.");
        }
        return normalized;
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }
}
