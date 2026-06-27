package com.holaclimbing.server.domain.user.oauth;

public record OAuthState(
        String provider,
        String redirectUri,
        String backendRedirectUri,
        String nonce
) {
    public static OAuthState of(OAuthProvider provider, String redirectUri, String backendRedirectUri, String nonce) {
        return new OAuthState(provider.value(), redirectUri, backendRedirectUri, nonce);
    }

    public OAuthProvider providerEnum() {
        return OAuthProvider.from(provider);
    }
}
