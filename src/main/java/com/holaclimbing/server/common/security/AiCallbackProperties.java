package com.holaclimbing.server.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 워커가 Spring 콜백 엔드포인트를 호출할 때 사용하는 공유 시크릿.
 */
@ConfigurationProperties(prefix = "ai")
public record AiCallbackProperties(String callbackSecret) {

    public boolean configured() {
        return callbackSecret != null && !callbackSecret.isBlank();
    }
}
