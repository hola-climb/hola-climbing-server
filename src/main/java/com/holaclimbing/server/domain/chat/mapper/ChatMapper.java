package com.holaclimbing.server.domain.chat.mapper;

import com.holaclimbing.server.domain.chat.domain.ChatMessage;
import com.holaclimbing.server.domain.chat.domain.ChatRoom;
import com.holaclimbing.server.domain.chat.domain.ChatRoomMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {

    /** 암장의 채팅방 조회. 없으면 null. */
    ChatRoom findRoomByGymId(Long gymId);

    /** 채팅방 단건 조회. 없으면 null. */
    ChatRoom findRoomById(Long id);

    /** 채팅방 저장. 생성된 PK는 room.id로 채워진다. */
    void insertRoom(ChatRoom room);

    /** 채팅방 멤버 조회. 없으면 null. */
    ChatRoomMember findMember(@Param("roomId") Long roomId, @Param("userId") Long userId);

    /** 멤버 등록. 이미 있으면 무시(ON CONFLICT DO NOTHING). */
    void insertMember(ChatRoomMember member);

    /** 메시지 저장. 생성된 PK는 message.id로 채워진다. */
    void insertMessage(ChatMessage message);

    /** 메시지 단건 조회. */
    ChatMessage findMessageById(Long id);

    /** 채팅방 메시지 목록 (최신순, 삭제 제외). */
    List<ChatMessage> findMessages(@Param("roomId") Long roomId,
                                   @Param("size") int size,
                                   @Param("offset") int offset);

    /** 채팅방 메시지 총 개수 (삭제 제외). */
    long countMessages(Long roomId);
}
