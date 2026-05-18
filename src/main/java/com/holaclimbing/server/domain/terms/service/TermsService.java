package com.holaclimbing.server.domain.terms.service;

import com.holaclimbing.server.domain.terms.dto.request.TermAgreementRequest;
import com.holaclimbing.server.domain.terms.dto.response.TermResponse;

import java.util.List;

public interface TermsService {

    /** 현재 발효 중인 약관 목록. */
    List<TermResponse> getActiveTerms();

    /** 약관 동의 기록. 유효하지 않은 termId는 거부한다. */
    void agree(Long userId, List<TermAgreementRequest> agreements);

    /** 필수 약관이 모두 동의됐는지 검증 (회원가입 시). 미동의 시 예외. */
    void validateRequiredAgreed(List<TermAgreementRequest> agreements);
}
