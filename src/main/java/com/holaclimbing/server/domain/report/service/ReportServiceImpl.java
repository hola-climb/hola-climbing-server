package com.holaclimbing.server.domain.report.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.report.domain.Report;
import com.holaclimbing.server.domain.report.dto.request.CreateReportRequest;
import com.holaclimbing.server.domain.report.dto.response.ReportResponse;
import com.holaclimbing.server.domain.report.mapper.ReportMapper;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.video.domain.Comment;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.mapper.CommentMapper;
import com.holaclimbing.server.domain.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final Set<String> TARGET_TYPES = Set.of("video", "comment", "user");
    private static final Set<String> CATEGORIES =
            Set.of("obscene", "copyright", "abuse", "spam", "etc");

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final CommentMapper commentMapper;

    @Override
    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        if (!TARGET_TYPES.contains(request.targetType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 신고 대상 유형입니다.");
        }
        if (!CATEGORIES.contains(request.category())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 신고 분류입니다.");
        }
        if (resolveTargetOwnerId(request.targetType(), request.targetId()).equals(reporterId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        if (reportMapper.existsByReporterAndTarget(reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(request.targetType())
                .targetId(request.targetId())
                .category(request.category())
                .reason(request.reason())
                .build();
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            // existsByReporterAndTarget 검사와 race가 발생해도 DB UNIQUE가 막아준다.
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        return ReportResponse.of(report);
    }

    /** 신고 대상의 소유자 userId. 대상이 없으면 예외. */
    private Long resolveTargetOwnerId(String targetType, Long targetId) {
        return switch (targetType) {
            case "user" -> {
                if (userMapper.findById(targetId) == null) {
                    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                }
                yield targetId;
            }
            case "video" -> {
                Video video = videoMapper.findById(targetId);
                if (video == null) {
                    throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
                }
                yield video.getUserId();
            }
            case "comment" -> {
                Comment comment = commentMapper.findById(targetId);
                if (comment == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
                }
                yield comment.getUserId();
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 신고 대상 유형입니다.");
        };
    }
}
