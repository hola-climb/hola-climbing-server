package com.holaclimbing.server.domain.user.oauth;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class OAuthProviderClientResolver {

    private final Map<OAuthProvider, OAuthProviderClient> clients = new EnumMap<>(OAuthProvider.class);

    public OAuthProviderClientResolver(List<OAuthProviderClient> clients) {
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    public OAuthProviderClient resolve(OAuthProvider provider) {
        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        return client;
    }
}
