package com.holaclimbing.server.domain.admin.dto.response;

import com.holaclimbing.server.domain.report.domain.Report;

import java.time.OffsetDateTime;

public record AdminReportResponse(
        Long id,
        Long reporterId,
        String targetType,
        Long targetId,
        String category,
        String reason,
        String status,
        Long reviewedBy,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt
) {

    public static AdminReportResponse from(Report report) {
        return new AdminReportResponse(
                report.getId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getCategory(),
                report.getReason(),
                report.getStatus(),
                report.getReviewedBy(),
                report.getReviewedAt(),
                report.getCreatedAt());
    }
}
