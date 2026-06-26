package com.holaclimbing.server.domain.report.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 신고 등록 요청.
 * targetType: video | comment | user
 * category: obscene | copyright | abuse | spam | irrelevant | etc
 */
public record CreateReportRequest(
        @NotBlank String targetType,
        @NotNull @Positive Long targetId,
        @NotBlank String category,
        @Size(max = 500) String reason
) {
}
