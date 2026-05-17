package com.holaclimbing.server.domain.chat.dto.response;

import com.holaclimbing.server.domain.chat.domain.ChatRoom;

public record ChatRoomResponse(
        Long id,
        Long gymId,
        String name
) {
    public static ChatRoomResponse of(ChatRoom room) {
        return new ChatRoomResponse(room.getId(), room.getGymId(), room.getName());
    }
}
