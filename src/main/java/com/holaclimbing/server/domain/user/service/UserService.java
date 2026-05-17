package com.holaclimbing.server.domain.user.service;

import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.response.SignupResponse;
import com.holaclimbing.server.domain.user.dto.response.TokenResponse;

public interface UserService {

    /** 회원가입 후 인증 메일 발송. */
    SignupResponse signup(SignupRequest request);

    /** 이메일/비밀번호 로그인 → Access/Refresh 토큰 발급. */
    TokenResponse login(LoginRequest request);

    /** Refresh 토큰으로 토큰 재발급. */
    TokenResponse refresh(String refreshToken);

    /** 이메일 인증 토큰 확인 → 인증 완료 처리. */
    void verifyEmail(String token);

    /** 인증 메일 재발송. */
    void resendVerification(String email);

    /** 이메일 사용 가능 여부 (미사용 시 true). */
    boolean isEmailAvailable(String email);

    /** 닉네임 사용 가능 여부 (미사용 시 true). */
    boolean isNicknameAvailable(String nickname);
}
