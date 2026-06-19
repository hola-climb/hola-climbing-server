package com.holaclimbing.server.domain.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 채팅 메시지 엔티티. chat_messages 테이블 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    private Long id;
    private Long roomId;
    private Long userId;
    private String content;
    private boolean verifiedAtGym;
    private OffsetDateTime createdAt;
    private OffsetDateTime deletedAt;
}
