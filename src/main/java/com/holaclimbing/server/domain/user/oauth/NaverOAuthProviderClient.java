package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NaverOAuthProviderClient extends AbstractOAuthProviderClient {

    public NaverOAuthProviderClient(RestClient.Builder builder, OAuthProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, objectMapper);
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public OAuthUserProfile fetchProfile(OAuthAuthorizationCodeRequest request) {
        String accessToken = exchangeCodeForAccessToken(request);
        JsonNode userInfo = getUserInfo(provider(), accessToken).path("response");
        return new OAuthUserProfile(
                provider(),
                userInfo.path("id").asText(),
                userInfo.path("email").asText(null),
                userInfo.path("nickname").asText(null),
                userInfo.path("profile_image").asText(null)
        );
    }
}
