package com.holaclimbing.server.domain.terms.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 약관 한 건에 대한 동의 여부.
 */
public record TermAgreementRequest(
        @NotNull Long termId,
        @NotNull Boolean agreed
) {
}
