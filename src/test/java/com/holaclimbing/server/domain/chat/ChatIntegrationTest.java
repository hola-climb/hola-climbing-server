package com.holaclimbing.server.domain.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.TestcontainersConfiguration;
import com.holaclimbing.server.domain.chat.dto.request.SendMessageRequest;
import com.holaclimbing.server.domain.chat.dto.response.ChatMessageResponse;
import com.holaclimbing.server.domain.chat.service.ChatService;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.request.VerifyEmailRequest;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MimeTypeUtils;
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

    @Autowired
    private ChatService chatService;

    @Value("${local.server.port}")
    private int port;

    /** 암장 1(TheClimb Gangnam) 좌표 — gyms-data.sql 기준. */
    private static final double GYM1_LAT = 37.4979;
    private static final double GYM1_LNG = 127.0276;

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

        // 브로드캐스트 payload는 raw 바이트로 받아 앱 ObjectMapper(snake_case)로 파싱한다.
        BlockingQueue<ChatMessageResponse> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/chat/" + roomId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    received.add(objectMapper.readValue((byte[]) payload, ChatMessageResponse.class));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        Thread.sleep(300);  // 구독 등록이 서버에 반영될 시간

        // 암장 좌표와 동일한 위치에서 전송 → verified_at_gym=true
        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.setDestination("/app/chat/" + roomId);
        sendHeaders.setContentType(MimeTypeUtils.APPLICATION_JSON);
        session.send(sendHeaders,
                objectMapper.writeValueAsBytes(new SendMessageRequest("first send!", GYM1_LAT, GYM1_LNG)));

        ChatMessageResponse delivered = received.poll(5, TimeUnit.SECONDS);
        assertThat(delivered).isNotNull();
        assertThat(delivered.content()).isEqualTo("first send!");
        assertThat(delivered.verifiedAtGym()).isTrue();

        mockMvc.perform(get("/api/chats/rooms/" + roomId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("first send!"))
                .andExpect(jsonPath("$.data.content[0].verified_at_gym").value(true));

        session.disconnect();
        client.stop();
    }

    @Test
    @DisplayName("GPS 인증 — 암장 반경 내/밖/위치없음에 따라 verifiedAtGym이 결정된다")
    void sendMessage_gpsVerification() throws Exception {
        String token = register("a@hola.com", "climberone");
        long roomId = joinRoom(token, 1L);
        Long userId = userMapper.findByEmail("a@hola.com").getId();

        // 암장 300m 반경 내 (약 14m)
        ChatMessageResponse near = chatService.sendMessage(roomId, userId,
                new SendMessageRequest("at the gym", GYM1_LAT + 0.0001, GYM1_LNG + 0.0001));
        assertThat(near.verifiedAtGym()).isTrue();

        // 암장에서 멀리 떨어진 위치
        ChatMessageResponse far = chatService.sendMessage(roomId, userId,
                new SendMessageRequest("far away", 37.5665, 126.9780));
        assertThat(far.verifiedAtGym()).isFalse();

        // 위치 미제공
        ChatMessageResponse noLocation = chatService.sendMessage(roomId, userId,
                new SendMessageRequest("no location", null, null));
        assertThat(noLocation.verifiedAtGym()).isFalse();
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

    /** 기본 SimpleMessageConverter 사용 — payload는 raw 바이트로 주고받는다. */
    private WebSocketStompClient stompClient() {
        return new WebSocketStompClient(new StandardWebSocketClient());
    }

    private long joinRoom(String token, long gymId) throws Exception {
        return dataOf(mockMvc.perform(post("/api/chats/gyms/" + gymId + "/join")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()))
                .path("id").asLong();
    }

    /** 회원가입 → 이메일 인증 → 로그인까지 완료하고 accessToken을 반환. */
    private String register(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupRequest(email, PASSWORD, nickname))))
                .andExpect(status().isCreated());
        var user = userMapper.findByEmail(email);
        mockMvc.perform(post("/api/auth/email/verify").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new VerifyEmailRequest(user.getEmailVerificationToken()))))
                .andExpect(status().isOk());
        return dataOf(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(email, PASSWORD)))))
                .path("access_token").asText();
    }

    private JsonNode dataOf(ResultActions actions) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }
}
