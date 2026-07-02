package com.holaclimbing.server.domain.chat.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.chat.dto.request.SendMessageRequest;
import com.holaclimbing.server.domain.chat.dto.response.ChatMessageResponse;
import com.holaclimbing.server.domain.chat.dto.response.ChatRoomResponse;

public interface ChatService {

    /** 암장 채팅방 입장. 방이 없으면 생성하고 멤버로 등록한다. */
    ChatRoomResponse joinGymRoom(Long userId, Long gymId);

    /** 채팅 메시지 저장. 암장 채팅방 멤버만 보낼 수 있다. */
    ChatMessageResponse sendMessage(Long gymId, Long userId, SendMessageRequest request);

    /** 암장 채팅방 메시지 이력 조회 (최신순). 방이 없으면 빈 목록. */
    PageResponse<ChatMessageResponse> getMessages(Long gymId, Long viewerId, int page, int size);
}
