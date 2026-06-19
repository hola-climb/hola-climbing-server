package com.holaclimbing.server.domain.chat.dto.response;

import com.holaclimbing.server.domain.chat.domain.ChatMessage;

import java.time.OffsetDateTime;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        Long userId,
        String content,
        boolean verifiedAtGym,
        OffsetDateTime createdAt
) {
    public static ChatMessageResponse of(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(), message.getRoomId(), message.getUserId(),
                message.getContent(), message.isVerifiedAtGym(), message.getCreatedAt());
    }
}
