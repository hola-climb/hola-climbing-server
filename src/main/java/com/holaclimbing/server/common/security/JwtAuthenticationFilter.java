package com.holaclimbing.server.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.ApiResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 요청 헤더의 "Authorization: Bearer <token>" 추출 → 검증 → SecurityContext 설정.
 * - 토큰 없음: anonymous로 통과 (보호 API는 EntryPoint가 401)
 * - 토큰 만료/위변조: 필터에서 직접 401 응답 (사용자에게 정확한 사유 전달)
 * - Refresh 토큰을 Access 자리에 보내면: INVALID_TOKEN (U005)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String ROLE_USER = "ROLE_USER";

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final TokenBlacklist tokenBlacklist;
    private final UserTokenRevoker userTokenRevoker;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);

        if (!StringUtils.hasText(token)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            // 한 번만 parseClaims로 검증 + 클레임 추출 (HMAC 검증을 3중복 호출하던 부분 정리).
            Claims claims = tokenProvider.parseClaims(token);
            if (!JwtTokenProvider.TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))) {
                sendError(response, ErrorCode.INVALID_TOKEN);
                return;
            }
            if (tokenBlacklist.contains(claims.getId())) {
                log.debug("Blacklisted JWT (logged out)");
                sendError(response, ErrorCode.INVALID_TOKEN);
                return;
            }

            Long userId = Long.parseLong(claims.getSubject());
            // 비밀번호 재설정·탈퇴 등으로 사용자 단위 revoke가 표시돼 있고 토큰 iat가 그보다 이르면 거부.
            long issuedAtSec = claims.getIssuedAt().toInstant().getEpochSecond();
            if (userTokenRevoker.isRevoked(userId, issuedAtSec)) {
                log.debug("Revoked JWT (user-level invalidation) — userId={}", userId);
                sendError(response, ErrorCode.INVALID_TOKEN);
                return;
            }

            var auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority(ROLE_USER)));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException e) {
            log.debug("Expired JWT: {}", e.getMessage());
            sendError(response, ErrorCode.EXPIRED_TOKEN);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            sendError(response, ErrorCode.INVALID_TOKEN);
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length());
        }
        return null;
    }

    private void sendError(HttpServletResponse response, ErrorCode ec) throws IOException {
        response.setStatus(ec.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        var body = ApiResponse.error(ec.getCode(), ec.getDefaultMessage());
        objectMapper.writeValue(response.getWriter(), body);
    }
}