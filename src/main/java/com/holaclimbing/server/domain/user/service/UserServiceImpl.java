package com.holaclimbing.server.domain.user.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.security.JwtTokenProvider;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.response.SignupResponse;
import com.holaclimbing.server.domain.user.dto.response.TokenResponse;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.infrastructure.mail.VerificationEmailSender;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final VerificationEmailSender emailSender;

    @Override
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userMapper.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userMapper.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        String verificationToken = UUID.randomUUID().toString();
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .emailVerified(false)
                .emailVerificationToken(verificationToken)
                .nickname(request.nickname())
                .build();
        userMapper.insert(user);

        emailSender.send(user.getEmail(), verificationToken);
        log.info("회원가입 완료: userId={}, email={}", user.getId(), user.getEmail());
        return SignupResponse.from(user);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        userMapper.updateLastLoginAt(user.getId());
        return issueTokens(user);
    }

    @Override
    public TokenResponse refresh(String refreshToken) {
        if (!parseIsRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Long userId = tokenProvider.getUserId(refreshToken);
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        User user = userMapper.findByEmailVerificationToken(token);
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        userMapper.markEmailVerified(user.getId());
        log.info("이메일 인증 완료: userId={}", user.getId());
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 인증이 완료된 계정입니다.");
        }
        String verificationToken = UUID.randomUUID().toString();
        userMapper.updateEmailVerificationToken(user.getId(), verificationToken);
        emailSender.send(email, verificationToken);
    }

    @Override
    public boolean isEmailAvailable(String email) {
        return !userMapper.existsByEmail(email);
    }

    @Override
    public boolean isNicknameAvailable(String nickname) {
        return !userMapper.existsByNickname(nickname);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = tokenProvider.createRefreshToken(user.getId());
        return TokenResponse.of(accessToken, refreshToken);
    }

    private boolean parseIsRefreshToken(String token) {
        try {
            return tokenProvider.isRefreshToken(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
