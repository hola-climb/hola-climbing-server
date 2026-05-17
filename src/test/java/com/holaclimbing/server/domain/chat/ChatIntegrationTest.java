package com.holaclimbing.server.domain.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.chat.dto.request.SendMessageRequest;
import com.holaclimbing.server.domain.chat.dto.response.ChatMessageResponse;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat 도메인 통합 테스트.
 * 채팅방 입장·이력 조회(REST)와 STOMP 실시간 메시지 송수신을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = {
        "classpath:sql/users-schema.sql",
        "classpath:sql/gyms-schema.sql",
        "classpath:sql/gyms-data.sql",
        "classpath:sql/chat-schema.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ChatIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("채팅방 입장 — 암장 채팅방에 입장하면 방 정보를 받는다")
    void joinGymRoom_success() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/chats/gyms/1/join").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.gym_id").value(1));
    }

    @Test
    @DisplayName("채팅방 입장 — 토큰 없이 호출하면 401")
    void joinGymRoom_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/chats/gyms/1/join"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("채팅방 입장 — 없는 암장은 404 G001")
    void joinGymRoom_nonexistentGym_returns404() throws Exception {
        String token = register("a@hola.com", "climberone");

        mockMvc.perform(post("/api/chats/gyms/999999/join").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("채팅방 입장 — 같은 암장에 다시 입장해도 동일한 방을 반환한다")
    void joinGymRoom_twice_sameRoom() throws Exception {
        String token = register("a@hola.com", "climberone");

        long first = joinRoom(token, 1L);
        long second = joinRoom(token, 1L);
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("메시지 이력 — 입장 직후 방은 메시지가 비어 있다")
    void getMessages_emptyRoom() throws Exception {
        String token = register("a@hola.com", "climberone");
        long roomId = joinRoom(token, 1L);

        mockMvc.perform(get("/api/chats/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(0));
    }

    @Test
    @DisplayName("STOMP — 메시지를 보내면 구독자에게 실시간 전달되고 이력에 저장된다")
    void stomp_sendAndBroadcast() throws Exception {
        String token = register("a@hola.com", "climberone");
        long roomId = joinRoom(token, 1L);

        WebSocketStompClient client = stompClient();
        StompSession session = client.connectAsync(
                        "ws://localhost:" + port + "/ws?token=" + token,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        BlockingQueue<ChatMessageResponse> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/chat/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((ChatMessageResponse) payload);
            }
        });
        Thread.sleep(300);  // 구독 등록이 서버에 반영될 시간

        session.send("/app/chat/" + roomId, new SendMessageRequest("first send!"));

        ChatMessageResponse delivered = received.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.content()).isEqualTo("first send!");

        mockMvc.perform(get("/api/chats/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("first send!"));

        session.disconnect();
        client.stop();
    }

    @Test
    @DisplayName("STOMP — 토큰 없이 연결하면 핸드셰이크가 거부된다")
    void stomp_withoutToken_handshakeRejected() {
        WebSocketStompClient client = stompClient();

        assertThatThrownBy(() -> client.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    // ===== helpers =====

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private long joinRoom(String token, long gymId) throws Exception {
        return dataOf(mockMvc.perform(post("/api/chats/gyms/" + gymId + "/join")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()))
                .path("id").asLong();
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(get("/api/users/verify-email").param("token", user.getEmailVerificationToken()))
                .andExpect(status().isOk());
        return dataOf(mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD)))))
                .path("access_token").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
