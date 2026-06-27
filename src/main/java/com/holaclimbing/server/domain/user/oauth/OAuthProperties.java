package com.holaclimbing.server.domain.user.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(
        int stateTtlMinutes,
        int resultTtlMinutes,
        int pendingSignupTtlMinutes,
        List<String> allowedRedirectUris,
        Map<String, Provider> providers
) {
    public Provider provider(OAuthProvider provider) {
        return providers == null ? null : providers.get(provider.value());
    }

    public boolean isAllowedRedirectUri(String redirectUri) {
        if (allowedRedirectUris == null || redirectUri == null) {
            return false;
        }
        return allowedRedirectUris.stream()
                .map(String::trim)
                .anyMatch(redirectUri::equals);
    }

    public record Provider(
            String clientId,
            String clientSecret,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            List<String> scope,
            String jwksUri,
            String responseMode,
            String teamId,
            String keyId,
            String privateKeyBase64,
            Integer clientSecretTtlDays
    ) {
        public String scopeValue() {
            if (scope == null || scope.isEmpty()) {
                return null;
            }
            return String.join(" ", scope);
        }

        public int effectiveClientSecretTtlDays() {
            return clientSecretTtlDays == null ? 30 : clientSecretTtlDays;
        }
    }
}
