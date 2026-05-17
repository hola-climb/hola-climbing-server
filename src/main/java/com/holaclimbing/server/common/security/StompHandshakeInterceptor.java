package com.holaclimbing.server.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebSocket 핸드셰이크 시 JWT를 검증한다.
 * 브라우저 WebSocket은 헤더를 못 붙이므로 토큰은 {@code ?token=} 쿼리 파라미터로 받는다.
 * 검증에 성공하면 userId를 세션 속성에 저장하고, 실패하면 핸드셰이크를 거부(403)한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandshakeInterceptor implements HandshakeInterceptor {

    /** 세션 속성에 저장되는 userId 키. STOMP 메시지 핸들러에서 꺼내 쓴다. */
    public static final String SESSION_USER_ID = "userId";

    private static final String TOKEN_PARAM = "token=";

    private final JwtTokenProvider tokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            return false;
        }
        try {
            if (!tokenProvider.isAccessToken(token)) {
                return false;
            }
            attributes.put(SESSION_USER_ID, tokenProvider.getUserId(token));
            return true;
        } catch (RuntimeException e) {
            log.debug("WebSocket 핸드셰이크 JWT 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            if (param.startsWith(TOKEN_PARAM)) {
                return URLDecoder.decode(param.substring(TOKEN_PARAM.length()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
