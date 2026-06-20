package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

abstract class AbstractOAuthProviderClient implements OAuthProviderClient {

    private static final Logger log = LoggerFactory.getLogger(AbstractOAuthProviderClient.class);
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final RestClient restClient;
    private final OAuthProperties properties;
    private final ObjectMapper objectMapper;

    protected AbstractOAuthProviderClient(RestClient.Builder builder, OAuthProperties properties, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    protected String exchangeCodeForAccessToken(OAuthAuthorizationCodeRequest request) {
        OAuthProperties.Provider providerProperties = properties.provider(request.provider());
        if (providerProperties == null || isBlank(providerProperties.clientId()) || isBlank(providerProperties.tokenUri())) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", providerProperties.clientId());
        if (!isBlank(providerProperties.clientSecret())) {
            body.add("client_secret", providerProperties.clientSecret());
        }
        body.add("code", request.code());
        body.add("redirect_uri", request.redirectUri());

        try {
            String responseBody = restClient.post()
                    .uri(providerProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode response = parseJson(request.provider(), "token exchange", responseBody);
            String accessToken = response == null ? null : response.path("access_token").asText(null);
            if (isBlank(accessToken)) {
                throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
            }
            return accessToken;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("OAuth token exchange failed: provider={}, status={}, body={}",
                    request.provider().value(), e.getStatusCode(), abbreviate(e.getResponseBodyAsString()));
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        } catch (Exception e) {
            log.warn("OAuth token exchange failed: provider={}, error={}",
                    request.provider().value(), e.toString());
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    protected JsonNode getUserInfo(OAuthProvider provider, String accessToken) {
        OAuthProperties.Provider providerProperties = properties.provider(provider);
        if (providerProperties == null || isBlank(providerProperties.userInfoUri())) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        try {
            String responseBody = restClient.get()
                    .uri(providerProperties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);
            return parseJson(provider, "userinfo", responseBody);
        } catch (RestClientResponseException e) {
            log.warn("OAuth userinfo request failed: provider={}, status={}, body={}",
                    provider.value(), e.getStatusCode(), abbreviate(e.getResponseBodyAsString()));
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        } catch (Exception e) {
            log.warn("OAuth userinfo request failed: provider={}, error={}",
                    provider.value(), e.toString());
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= MAX_ERROR_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }

    private JsonNode parseJson(OAuthProvider provider, String step, String responseBody) {
        if (isBlank(responseBody)) {
            log.warn("OAuth {} returned empty response body: provider={}", step, provider.value());
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            log.warn("OAuth {} returned invalid JSON: provider={}, bodyLength={}, error={}",
                    step, provider.value(), responseBody.length(), e.toString());
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }
}
