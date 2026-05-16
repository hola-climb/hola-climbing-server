package com.holaclimbing.server.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 사용자가 보호 API에 접근 시 호출 → 401 응답.
 * (토큰이 명시적으로 잘못된 경우는 필터에서 직접 처리하므로 여기는 "토큰 없음" 케이스)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("Unauthorized: {} {}", request.getMethod(), request.getRequestURI());

        ErrorCode ec = ErrorCode.UNAUTHORIZED;
        response.setStatus(ec.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        var body = ApiResponse.error(ec.getCode(), ec.getDefaultMessage());
        objectMapper.writeValue(response.getWriter(), body);
    }
}