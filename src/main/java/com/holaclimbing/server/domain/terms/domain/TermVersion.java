package com.holaclimbing.server.domain.terms.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 약관 버전. terms_versions 테이블 매핑.
 * type: service | privacy | marketing | location
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermVersion {

    private Long id;
    private String type;
    private String version;
    private String title;
    private String content;
    private Boolean isRequired;
    private OffsetDateTime effectiveAt;
}
