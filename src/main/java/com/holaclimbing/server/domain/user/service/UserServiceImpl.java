package com.holaclimbing.server.domain.user.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.security.JwtTokenProvider;
import com.holaclimbing.server.common.security.TokenBlacklist;
import com.holaclimbing.server.common.security.UserTokenRevoker;
import com.holaclimbing.server.domain.terms.service.TermsService;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.response.SignupResponse;
import com.holaclimbing.server.domain.user.dto.response.TokenResponse;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.infrastructure.mail.VerificationEmailSender;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String PWRESET_PREFIX = "auth:pwreset:";
    private static final Duration PWRESET_TTL = Duration.ofMinutes(30);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final VerificationEmailSender emailSender;
    private final StringRedisTemplate redis;
    private final TokenBlacklist tokenBlacklist;
    private final UserTokenRevoker userTokenRevoker;
    private final TermsService termsService;

    @Override
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userMapper.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userMapper.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        termsService.validateRequiredAgreed(request.termsAgreed());

        String verificationToken = UUID.randomUUID().toString();
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .emailVerified(false)
                .emailVerificationToken(verificationToken)
                .nickname(request.nickname())
                .build();
        userMapper.insert(user);
        termsService.agree(user.getId(), request.termsAgreed());

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

    /**
     * Refresh 토큰 회전(rotation). 토큰을 재발급하면서 사용된 refresh 토큰을 1회용으로 폐기한다.
     * <ul>
     *   <li>재사용 탐지: 이미 회전되어 블랙리스트된 refresh 토큰이 다시 들어오면 탈취 신호로 보고 거부.</li>
     *   <li>회전: 검증을 통과한 refresh 토큰의 jti를 잔여 수명 동안 블랙리스트에 등록 → 같은 토큰 재사용 불가.</li>
     * </ul>
     */
    @Override
    public TokenResponse refresh(String refreshToken) {
        if (!parseIsRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Claims claims = tokenProvider.parseClaims(refreshToken);
        String jti = claims.getId();
        // 재사용 탐지 — 이미 회전된(블랙리스트된) refresh 토큰이면 거부.
        if (tokenBlacklist.contains(jti)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Long userId = tokenProvider.getUserId(refreshToken);
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 회전 — 사용된 refresh 토큰을 잔여 수명 동안 블랙리스트에 등록(1회용).
        long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        tokenBlacklist.blacklist(jti, Duration.ofMillis(remainingMs));
        return issueTokens(user);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        blacklist(accessToken);
        blacklist(refreshToken);
    }

    private void blacklist(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Claims claims = tokenProvider.parseClaims(token);
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            tokenBlacklist.blacklist(claims.getId(), Duration.ofMillis(remainingMs));
        } catch (JwtException | IllegalArgumentException e) {
            // 만료·위변조 토큰은 이미 무효이므로 블랙리스트 등록 불필요.
            log.debug("로그아웃 — 무효 토큰 무시: {}", e.getMessage());
        }
    }

    @Override
    public void requestPasswordReset(String email) {
        User user = userMapper.findByEmail(email);
        if (user == null) {
            // 보안: 가입되지 않은 이메일도 동일한 응답을 준다.
            return;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(PWRESET_PREFIX + token, String.valueOf(user.getId()), PWRESET_TTL);
        emailSender.sendPasswordReset(email, token);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String key = PWRESET_PREFIX + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
        }
        Long id = Long.valueOf(userId);
        userMapper.updatePassword(id, passwordEncoder.encode(newPassword));
        // 비밀번호 변경 → 기존에 발급된 모든 access/refresh 토큰 즉시 무효화.
        // 탈취된 계정 복구 시나리오에서 공격자의 잔여 토큰을 차단한다.
        userTokenRevoker.revokeAllFor(id);
        redis.delete(key);
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
