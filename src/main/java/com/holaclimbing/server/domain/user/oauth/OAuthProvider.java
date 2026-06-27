package com.holaclimbing.server.domain.user.oauth;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;

import java.util.Arrays;

public enum OAuthProvider {
    GOOGLE("google"),
    KAKAO("kakao"),
    NAVER("naver"),
    APPLE("apple");

    private final String value;

    OAuthProvider(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static OAuthProvider from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }
}
