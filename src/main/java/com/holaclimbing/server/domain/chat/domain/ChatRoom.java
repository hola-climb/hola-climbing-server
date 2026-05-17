package com.holaclimbing.server.domain.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅방 엔티티. chat_rooms 테이블 매핑. 암장당 1개 (gym_id UNIQUE).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    private Long id;
    private Long gymId;
    private String name;
    private LocalDateTime createdAt;
}
