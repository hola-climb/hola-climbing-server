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
import com.holaclimbing.server.domain.gym.domain.Gym;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /**
     * 암장 인증 반경 (미터). 이 거리 안에서 보낸 메시지는 verifiedAtGym=true.
     *
     * <p><b>보안 한계 (의도된 동작):</b> 좌표는 클라이언트가 STOMP 메시지에 실어 보낸 값을
     * 그대로 신뢰한다. 모바일 앱에서 위치를 위조하면 false-positive가 가능하므로
     * {@code verifiedAtGym}은 채팅 UI의 "현장 인증" 뱃지 표시용일 뿐 권한 결정에 쓰이지 않는다.
     * 실효성 있는 위치 인증이 필요해지면 모바일 attest API + 서버 IP-geolocation 교차검증을
     * 도입해야 한다. (TODO: product decision)</p>
     */
    private static final double GYM_VERIFY_RADIUS_M = 500;
    private static final double EARTH_RADIUS_M = 6_371_000;

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
            ChatRoom candidate = ChatRoom.builder().gymId(gymId).build();
            chatMapper.insertRoom(candidate);
            // ON CONFLICT DO NOTHING — 다른 트랜잭션이 먼저 만들었으면 id가 안 채워진다. 재조회로 정합.
            room = candidate.getId() != null ? candidate : chatMapper.findRoomByGymId(gymId);
        }
        chatMapper.insertMember(ChatRoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .build());
        return ChatRoomResponse.of(room);
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long gymId, Long userId, SendMessageRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지 내용이 비어 있습니다.");
        }
        ChatRoom room = chatMapper.findRoomByGymId(gymId);
        if (room == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "채팅방을 찾을 수 없습니다.");
        }
        ChatRoomMember member = chatMapper.findMember(room.getId(), userId);
        if (member == null || member.getLeftAt() != null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "채팅방 멤버가 아닙니다.");
        }
        ChatMessage message = ChatMessage.builder()
                .roomId(room.getId())
                .userId(userId)
                .content(request.content().strip())
                .verifiedAtGym(verifyAtGym(gymId, request.lat(), request.lng()))
                .build();
        chatMapper.insertMessage(message);
        return ChatMessageResponse.of(chatMapper.findMessageById(message.getId()));
    }

    /** 보낸 위치가 암장 {@value #GYM_VERIFY_RADIUS_M}m 반경 내인지 판정. 위치 미제공 시 false. */
    private boolean verifyAtGym(Long gymId, Double lat, Double lng) {
        if (lat == null || lng == null) {
            return false;
        }
        Gym gym = gymMapper.findById(gymId);
        if (gym == null || gym.getLat() == null || gym.getLng() == null) {
            return false;
        }
        return distanceMeters(gym.getLat(), gym.getLng(), lat, lng) <= GYM_VERIFY_RADIUS_M;
    }

    /** 두 좌표 간 거리(미터) — Haversine. */
    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    @Override
    public PageResponse<ChatMessageResponse> getMessages(Long gymId, int page, int size) {
        ChatRoom room = chatMapper.findRoomByGymId(gymId);
        if (room == null) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        long total = chatMapper.countMessages(room.getId());
        List<ChatMessageResponse> content = chatMapper.findMessages(room.getId(), size, page * size)
                .stream().map(ChatMessageResponse::of).toList();
        return PageResponse.of(content, page, size, total);
    }
}
