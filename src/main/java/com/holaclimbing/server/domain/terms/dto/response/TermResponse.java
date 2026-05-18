package com.holaclimbing.server.domain.terms.dto.response;

import com.holaclimbing.server.domain.terms.domain.TermVersion;

public record TermResponse(
        Long termId,
        String type,
        String version,
        boolean required,
        String title
) {
    public static TermResponse of(TermVersion term) {
        return new TermResponse(term.getId(), term.getType(), term.getVersion(),
                Boolean.TRUE.equals(term.getIsRequired()), term.getTitle());
    }
}
