package com.holaclimbing.server.domain.chat;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.chat.dto.response.ChatMessageResponse;
import com.holaclimbing.server.domain.chat.dto.response.ChatRoomResponse;
import com.holaclimbing.server.domain.chat.service.ChatService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅방 REST API. 실시간 메시지 송수신은 STOMP(/app/gyms/{id}/chat, /topic/gyms/{id}/chat)로 처리한다.
 * 모두 인증이 필요하다.
 */
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/gyms/{gymId}/join")
    public ApiResponse<ChatRoomResponse> joinGymRoom(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long gymId) {
        return ApiResponse.success(chatService.joinGymRoom(userId, gymId));
    }

    @GetMapping("/gyms/{gymId}/messages")
    public ApiResponse<PageResponse<ChatMessageResponse>> getMessages(
            @PathVariable Long gymId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Positive @Max(100) int size) {
        return ApiResponse.success(chatService.getMessages(gymId, page, size));
    }
}
