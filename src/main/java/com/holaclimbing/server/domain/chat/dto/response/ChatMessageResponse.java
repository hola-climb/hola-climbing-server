package com.holaclimbing.server.domain.chat.dto.response;

import com.holaclimbing.server.domain.chat.domain.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long userId,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse of(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(), message.getRoomId(), message.getUserId(),
                message.getContent(), message.getCreatedAt());
    }
}
