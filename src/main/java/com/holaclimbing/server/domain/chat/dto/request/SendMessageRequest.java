package com.holaclimbing.server.domain.chat.dto.request;

/**
 * STOMP 채팅 메시지 발행 요청. content 검증은 서비스 계층에서 수행한다.
 */
public record SendMessageRequest(String content) {
}
