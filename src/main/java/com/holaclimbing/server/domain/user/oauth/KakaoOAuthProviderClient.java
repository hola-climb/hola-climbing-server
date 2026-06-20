package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoOAuthProviderClient extends AbstractOAuthProviderClient {

    public KakaoOAuthProviderClient(RestClient.Builder builder, OAuthProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, objectMapper);
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuthUserProfile fetchProfile(OAuthAuthorizationCodeRequest request) {
        String accessToken = exchangeCodeForAccessToken(request);
        JsonNode userInfo = getUserInfo(provider(), accessToken);
        JsonNode account = userInfo.path("kakao_account");
        JsonNode profile = account.path("profile");
        return new OAuthUserProfile(
                provider(),
                userInfo.path("id").asText(),
                account.path("email").asText(null),
                profile.path("nickname").asText(null),
                profile.path("profile_image_url").asText(null)
        );
    }
}
