package com.holaclimbing.server.domain.chat.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.chat.domain.ChatMessage;
import com.holaclimbing.server.domain.chat.domain.ChatRoom;
import com.holaclimbing.server.domain.chat.domain.ChatRoomMember;
import com.holaclimbing.server.domain.chat.dto.request.SendMessageRequest;
import com.holaclimbing.server.domain.chat.dto.response.ChatMessageResponse;
import com.holaclimbing.server.domain.chat.dto.response.ChatRoomResponse;
import com.holaclimbing.server.domain.chat.mapper.ChatMapper;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMapper chatMapper;
    private final GymMapper gymMapper;

    @Override
    @Transactional
    public ChatRoomResponse joinGymRoom(Long userId, Long gymId) {
        if (gymMapper.findById(gymId) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        ChatRoom room = chatMapper.findRoomByGymId(gymId);
        if (room == null) {
            room = ChatRoom.builder().gymId(gymId).build();
            chatMapper.insertRoom(room);
        }
        chatMapper.insertMember(ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .build());
        return ChatRoomResponse.of(room);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long roomId, Long userId, SendMessageRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지 내용이 비어 있습니다.");
        }
        if (chatMapper.findRoomById(roomId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다.");
        }
        ChatRoomMember member = chatMapper.findMember(roomId, userId);
        if (member == null || member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "채팅방 멤버가 아닙니다.");
        }
        ChatMessage message = ChatMessage.builder()
                .roomId(roomId)
                .userId(userId)
                .content(request.content().strip())
                .build();
        chatMapper.insertMessage(message);
        return ChatMessageResponse.of(chatMapper.findMessageById(message.getId()));
    }

    @Override
    public PageResponse<ChatMessageResponse> getMessages(Long roomId, int page, int size) {
        if (chatMapper.findRoomById(roomId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다.");
        }
        long total = chatMapper.countMessages(roomId);
        List<ChatMessageResponse> content = chatMapper.findMessages(roomId, size, page * size)
                .stream().map(ChatMessageResponse::of).toList();
        return PageResponse.of(content, page, size, total);
    }
}
