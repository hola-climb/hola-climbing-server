package com.holaclimbing.server.domain.user.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class GoogleOAuthProviderClientTest {

    private HttpServer server;
    private GoogleOAuthProviderClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", this::handleToken);
        server.createContext("/userinfo", this::handleUserInfo);
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        OAuthProperties properties = new OAuthProperties(
                5,
                1,
                10,
                List.of("https://hola-climb.app/oauth/callback"),
                Map.of("google", new OAuthProperties.Provider(
                        "google-client-id",
                        "google-client-secret",
                        "https://accounts.google.com/o/oauth2/v2/auth",
                        baseUrl + "/token",
                        baseUrl + "/userinfo",
                        List.of("openid", "email", "profile")
                ))
        );
        client = new GoogleOAuthProviderClient(RestClient.builder(), properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchProfile_parsesTokenAndUserInfoJsonResponses() {
        OAuthUserProfile profile = client.fetchProfile(new OAuthAuthorizationCodeRequest(
                OAuthProvider.GOOGLE,
                "provider-code",
                "https://hola-climb.app/api/auth/oauth/google/callback"
        ));

        assertThat(profile.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(profile.providerId()).isEqualTo("google-sub");
        assertThat(profile.email()).isEqualTo("google@hola.com");
        assertThat(profile.nickname()).isEqualTo("Google User");
        assertThat(profile.profileImage()).isEqualTo("https://example.com/google.png");
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(requestBody).contains("grant_type=authorization_code");
        assertThat(requestBody).contains("client_id=google-client-id");
        assertThat(requestBody).contains("code=provider-code");

        respond(exchange, 200, """
                {"access_token":"google-access-token","token_type":"Bearer","expires_in":3600}
                """);
    }

    private void handleUserInfo(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("GET");
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer google-access-token");

        respond(exchange, 200, """
                {
                  "sub": "google-sub",
                  "email": "google@hola.com",
                  "name": "Google User",
                  "picture": "https://example.com/google.png"
                }
                """);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
