package com.holaclimbing.server.domain.chat;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.security.StompHandshakeInterceptor;
import com.holaclimbing.server.domain.chat.dto.request.SendMessageRequest;
import com.holaclimbing.server.domain.chat.dto.response.ChatMessageResponse;
import com.holaclimbing.server.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * STOMP 채팅 메시지 핸들러.
 * 클라이언트가 /app/gyms/{gymId}/chat로 발행하면 저장 후
 * /topic/gyms/{gymId}/chat 구독자에게 브로드캐스트한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;

    @MessageMapping("/gyms/{gymId}/chat")
    @SendTo("/topic/gyms/{gymId}/chat")
    public ChatMessageResponse send(@DestinationVariable Long gymId,
                                    SendMessageRequest request,
                                    SimpMessageHeaderAccessor headerAccessor) {
        Long userId = (Long) headerAccessor.getSessionAttributes()
                .get(StompHandshakeInterceptor.SESSION_USER_ID);
        return chatService.sendMessage(gymId, userId, request);
    }

    /** 메시지 처리 실패(비멤버·빈 내용 등)는 브로드캐스트하지 않고 로그만 남긴다. */
    @MessageExceptionHandler(BusinessException.class)
    public void handleBusinessException(BusinessException e) {
        log.warn("채팅 메시지 처리 실패: {}", e.getMessage());
    }
}
