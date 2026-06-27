package com.holaclimbing.server.domain.user.oauth;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthProviderClientResolverTest {

    @Test
    void resolve_returnsRegisteredClient() {
        OAuthProviderClient googleClient = fakeGoogleClient();
        OAuthProviderClientResolver resolver = new OAuthProviderClientResolver(List.of(googleClient));

        OAuthProviderClient resolved = resolver.resolve(OAuthProvider.GOOGLE);

        assertThat(resolved).isSameAs(googleClient);
    }

    @Test
    void resolve_missingProviderClientThrowsAuthorizationFailure() {
        OAuthProviderClientResolver resolver = new OAuthProviderClientResolver(List.of(fakeGoogleClient()));

        assertThatThrownBy(() -> resolver.resolve(OAuthProvider.APPLE))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OAUTH_AUTHORIZATION_FAILED));
    }

    private static OAuthProviderClient fakeGoogleClient() {
        return new OAuthProviderClient() {
            @Override
            public OAuthProvider provider() {
                return OAuthProvider.GOOGLE;
            }

            @Override
            public OAuthUserProfile fetchProfile(OAuthAuthorizationCodeRequest request) {
                throw new UnsupportedOperationException("Not needed for resolver tests");
            }
        };
    }
}
