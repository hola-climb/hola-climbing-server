package com.holaclimbing.server.domain.user.dto.request;

import com.holaclimbing.server.domain.terms.dto.request.TermAgreementRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 회원가입 요청. termsAgreed는 약관 동의 내역 — 필수 약관은 모두 동의돼야 한다.
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(min = 2, max = 20) String nickname,
        @Valid List<TermAgreementRequest> termsAgreed
) {
    /** 약관 동의 내역 없이 생성 (테스트 등). */
    public SignupRequest(String email, String password, String nickname) {
        this(email, password, nickname, List.of());
    }
}
