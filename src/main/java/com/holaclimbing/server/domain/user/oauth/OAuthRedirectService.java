package com.holaclimbing.server.domain.user.oauth;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.terms.service.TermsService;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.dto.request.OAuthSignupRequest;
import com.holaclimbing.server.domain.user.dto.response.OAuthLoginResponse;
import com.holaclimbing.server.domain.user.dto.response.TokenResponse;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import com.holaclimbing.server.domain.user.service.AuthTokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthRedirectService {

    private final OAuthProperties properties;
    private final OAuthStateStore stateStore;
    private final OAuthResultStore resultStore;
    private final OAuthPendingSignupStore pendingSignupStore;
    private final OAuthProviderClientResolver clientResolver;
    private final UserMapper userMapper;
    private final TermsService termsService;
    private final AuthTokenIssuer authTokenIssuer;

    @Value("${app.base-url}")
    private String appBaseUrl;

    public String buildAuthorizationRedirect(String providerValue, String frontendRedirectUri) {
        OAuthProvider provider = OAuthProvider.from(providerValue);
        if (!properties.isAllowedRedirectUri(frontendRedirectUri)) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        OAuthProperties.Provider providerProperties = properties.provider(provider);
        if (providerProperties == null
                || isBlank(providerProperties.clientId())
                || isBlank(providerProperties.authorizationUri())) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }

        String backendRedirectUri = backendCallbackUri(provider);
        String nonce = UUID.randomUUID().toString();
        String state = stateStore.issue(OAuthState.of(provider, frontendRedirectUri, backendRedirectUri, nonce));
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(providerProperties.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", providerProperties.clientId())
                .queryParam("redirect_uri", backendRedirectUri)
                .queryParam("state", state);
        String scope = providerProperties.scopeValue();
        if (!isBlank(scope)) {
            builder.queryParam("scope", scope);
        }
        if (!isBlank(providerProperties.responseMode())) {
            builder.queryParam("response_mode", providerProperties.responseMode());
        }
        if (provider == OAuthProvider.APPLE) {
            builder.queryParam("nonce", nonce);
        }
        return builder.encode().toUriString();
    }

    @Transactional
    public String handleCallback(String providerValue, String providerCode, String stateToken, String providerError) {
        OAuthProvider provider = OAuthProvider.from(providerValue);
        OAuthState state = stateStore.consume(stateToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OAUTH_STATE));
        if (state.providerEnum() != provider) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
        }
        if (!isBlank(providerError)) {
            return frontendRedirectWithError(state.redirectUri(), providerError);
        }
        if (isBlank(providerCode)) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }

        String backendRedirectUri = isBlank(state.backendRedirectUri())
                ? backendCallbackUri(provider)
                : state.backendRedirectUri();
        OAuthUserProfile profile = clientResolver.resolve(provider)
                .fetchProfile(new OAuthAuthorizationCodeRequest(provider, providerCode, backendRedirectUri));
        OAuthLoginResponse response = resolveLoginResponse(profile);
        String oauthCode = resultStore.issue(response);
        return UriComponentsBuilder.fromUriString(state.redirectUri())
                .queryParam("oauthCode", oauthCode)
                .build()
                .toUriString();
    }

    public OAuthLoginResponse consumeResult(String code) {
        return resultStore.consume(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OAUTH_RESULT_CODE));
    }

    @Transactional
    public TokenResponse signup(OAuthSignupRequest request) {
        OAuthPendingSignup pendingSignup = pendingSignupStore.consume(request.signupToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OAUTH_SIGNUP_TOKEN));
        OAuthUserProfile profile = pendingSignup.toProfile();

        User existingSocialUser = userMapper.findByProvider(profile.provider().value(), profile.providerId());
        if (existingSocialUser != null) {
            requireActive(existingSocialUser);
            userMapper.updateLastLoginAt(existingSocialUser.getId());
            return authTokenIssuer.issue(existingSocialUser);
        }
        if (profile.email() != null && userMapper.existsByEmail(profile.email())) {
            throw new BusinessException(ErrorCode.OAUTH_EMAIL_ALREADY_EXISTS);
        }
        if (userMapper.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        termsService.validateRequiredAgreed(request.termsAgreed());

        User user = User.builder()
                .email(profile.email())
                .emailVerified(profile.email() != null)
                .provider(profile.provider().value())
                .providerId(profile.providerId())
                .nickname(request.nickname())
                .profileImage(profile.profileImage())
                .build();
        userMapper.insertOAuth(user);
        termsService.agree(user.getId(), request.termsAgreed());
        userMapper.updateLastLoginAt(user.getId());
        return authTokenIssuer.issue(userMapper.findById(user.getId()));
    }

    private OAuthLoginResponse resolveLoginResponse(OAuthUserProfile profile) {
        User user = userMapper.findByProvider(profile.provider().value(), profile.providerId());
        if (user != null) {
            requireActive(user);
            userMapper.updateLastLoginAt(user.getId());
            return OAuthLoginResponse.loggedIn(authTokenIssuer.issue(user));
        }
        if (profile.email() != null && userMapper.existsByEmail(profile.email())) {
            return OAuthLoginResponse.emailAlreadyExists(
                    profile.email(),
                    profile.nickname(),
                    profile.profileImage()
            );
        }
        String signupToken = pendingSignupStore.issue(OAuthPendingSignup.from(profile));
        return OAuthLoginResponse.signupRequired(
                signupToken,
                profile.email(),
                profile.nickname(),
                profile.profileImage()
        );
    }

    private void requireActive(User user) {
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
    }

    private String backendCallbackUri(OAuthProvider provider) {
        return UriComponentsBuilder.fromUriString(appBaseUrl)
                .path("/api/auth/oauth/{provider}/callback")
                .buildAndExpand(provider.value())
                .toUriString();
    }

    private String frontendRedirectWithError(String frontendRedirectUri, String error) {
        return UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("oauthError", error)
                .build()
                .toUriString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
