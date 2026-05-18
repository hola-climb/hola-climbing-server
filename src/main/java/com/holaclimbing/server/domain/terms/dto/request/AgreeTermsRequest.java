package com.holaclimbing.server.domain.terms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 약관 동의 기록 요청.
 */
public record AgreeTermsRequest(
        @NotEmpty @Valid List<TermAgreementRequest> agreements
) {
}
