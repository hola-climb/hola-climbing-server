package com.holaclimbing.server.domain.terms.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.terms.domain.TermVersion;
import com.holaclimbing.server.domain.terms.dto.request.TermAgreementRequest;
import com.holaclimbing.server.domain.terms.dto.response.TermResponse;
import com.holaclimbing.server.domain.terms.mapper.TermsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TermsServiceImpl implements TermsService {

    private final TermsMapper termsMapper;

    @Override
    public List<TermResponse> getActiveTerms() {
        return findActiveTermsOrThrow().stream().map(TermResponse::of).toList();
    }

    @Override
    @Transactional
    public void agree(Long userId, List<TermAgreementRequest> agreements) {
        List<TermVersion> activeTerms = findActiveTermsOrThrow();
        if (agreements == null || agreements.isEmpty()) {
            return;
        }
        Map<Long, TermVersion> active = activeTerms.stream()
                .collect(Collectors.toMap(TermVersion::getId, Function.identity()));
        for (TermAgreementRequest a : agreements) {
            if (!active.containsKey(a.termId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "유효하지 않은 약관입니다: " + a.termId());
            }
            termsMapper.upsertAgreement(userId, a.termId(), Boolean.TRUE.equals(a.agreed()));
        }
    }

    @Override
    public void validateRequiredAgreed(List<TermAgreementRequest> agreements) {
        List<TermVersion> activeTerms = findActiveTermsOrThrow();
        if (activeTerms.stream().noneMatch(term -> Boolean.TRUE.equals(term.getIsRequired()))) {
            throw new BusinessException(ErrorCode.TERMS_NOT_CONFIGURED);
        }

        Set<Long> agreedTermIds = agreements == null ? Set.of()
                : agreements.stream()
                .filter(a -> Boolean.TRUE.equals(a.agreed()))
                .map(TermAgreementRequest::termId)
                .collect(Collectors.toSet());
        for (TermVersion term : activeTerms) {
            if (Boolean.TRUE.equals(term.getIsRequired()) && !agreedTermIds.contains(term.getId())) {
                throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
            }
        }
    }

    private List<TermVersion> findActiveTermsOrThrow() {
        List<TermVersion> activeTerms = termsMapper.findActiveTerms();
        if (activeTerms.isEmpty()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_CONFIGURED);
        }
        return activeTerms;
    }
}
