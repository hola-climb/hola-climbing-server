package com.holaclimbing.server.domain.terms.mapper;

import com.holaclimbing.server.domain.terms.domain.TermVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TermsMapper {

    /** 현재 발효 중인 약관 — type별 최신 버전. */
    List<TermVersion> findActiveTerms();

    /** 약관 동의 기록 upsert. 같은 (user, term)이면 갱신. */
    void upsertAgreement(@Param("userId") Long userId,
                         @Param("termId") Long termId,
                         @Param("agreed") boolean agreed);
}
