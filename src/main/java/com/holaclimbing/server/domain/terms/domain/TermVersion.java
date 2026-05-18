package com.holaclimbing.server.domain.terms.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 약관 버전. terms_versions 테이블 매핑.
 * type: service | privacy | marketing
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
    private Boolean isRequired;
    private LocalDateTime effectiveAt;
}
