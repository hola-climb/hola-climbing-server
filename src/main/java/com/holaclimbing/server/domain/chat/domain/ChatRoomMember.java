package com.holaclimbing.server.domain.chat.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 채팅방 멤버 엔티티. chat_room_members 테이블 매핑.
 * leftAt이 채워지면 퇴장 상태.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMember {

    private Long id;
    private Long roomId;
    private Long userId;
    private LocalDateTime joinedAt;
    private Long lastReadMessageId;
    private LocalDateTime leftAt;
}
