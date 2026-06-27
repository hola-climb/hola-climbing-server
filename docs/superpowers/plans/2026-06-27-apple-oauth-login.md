# Apple OAuth Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add Sign in with Apple to the existing backend OAuth redirect flow without changing the frontend handoff contract.

**Architecture:** Reuse the current `/api/auth/oauth/{provider}/authorize -> provider callback -> one-time oauthCode -> /api/auth/oauth/result` flow. Add Apple as a provider-specific client because Apple requires an ES256 client secret JWT and RS256 `id_token` verification instead of the current access-token-plus-userinfo pattern.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Spring MVC, Spring Security, JJWT 0.12.6, RestClient, Redis-backed OAuth state/result stores, MyBatis, JUnit 5.

---

## Current Context

Existing OAuth files:
- `src/main/java/com/holaclimbing/server/domain/user/OAuthRedirectController.java`: provider-neutral authorize/callback/result/signup endpoints.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthRedirectService.java`: Redis state/result/pending signup orchestration.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/AbstractOAuthProviderClient.java`: token exchange and userinfo helper for Google/Kakao/Naver.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/GoogleOAuthProviderClient.java`
- `src/main/java/com/holaclimbing/server/domain/user/oauth/KakaoOAuthProviderClient.java`
- `src/main/java/com/holaclimbing/server/domain/user/oauth/NaverOAuthProviderClient.java`
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProvider.java`
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProperties.java`
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthAuthorizationCodeRequest.java`
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthState.java`
- `src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java`
- `src/test/java/com/holaclimbing/server/domain/user/oauth/GoogleOAuthProviderClientTest.java`

Apple OpenID endpoints from `https://appleid.apple.com/.well-known/openid-configuration`:
- authorization endpoint: `https://appleid.apple.com/auth/authorize`
- token endpoint: `https://appleid.apple.com/auth/token`
- JWKS endpoint: `https://appleid.apple.com/auth/keys`
- supported scopes: `openid`, `email`, `name`
- supported response modes: `query`, `fragment`, `form_post`
- token endpoint auth method: `client_secret_post`

Keep existing account policy:
- A matching `(provider, provider_id)` logs in.
- A new social account returns `SIGNUP_REQUIRED`.
- A social email matching a local email returns `EMAIL_ALREADY_EXISTS`.
- Do not auto-link Apple to local email accounts.

## File Structure

Create:
- `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleClientSecretGenerator.java`: builds Apple `client_secret` JWT with ES256.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/ApplePrivateKeyParser.java`: parses base64-wrapped Apple `.p8` private key material.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleJwksClient.java`: fetches and caches Apple public keys from JWKS.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenClaims.java`: typed subset of Apple `id_token` claims.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenVerifier.java`: verifies Apple `id_token` signature and claims.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleOAuthProviderClient.java`: exchanges code, verifies `id_token`, maps Apple identity to `OAuthUserProfile`.
- `src/test/java/com/holaclimbing/server/domain/user/oauth/AppleClientSecretGeneratorTest.java`
- `src/test/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenVerifierTest.java`
- `src/test/java/com/holaclimbing/server/domain/user/oauth/AppleOAuthProviderClientTest.java`

Modify:
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProvider.java`: add `APPLE("apple")`.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProperties.java`: add Apple-specific provider fields.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthState.java`: store nonce for Apple.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthAuthorizationCodeRequest.java`: carry nonce and Apple one-time `user` JSON.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/AbstractOAuthProviderClient.java`: expose token response and provider-specific client secret hook.
- `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthRedirectService.java`: include nonce/response_mode on authorize and pass Apple `user` JSON through callback handling.
- `src/main/java/com/holaclimbing/server/domain/user/OAuthRedirectController.java`: support Apple `form_post` callback.
- `src/main/resources/application.yaml`: add Apple OAuth properties.
- `README.md`: document Apple OAuth environment variables and provider list.
- `src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java`: add Apple redirect/callback/result/signup coverage.

No DB migration is needed. `users.provider VARCHAR(20)`, `users.provider_id VARCHAR(100)`, and `UNIQUE(provider, provider_id)` already support `provider='apple'`.

---

### Task 1: Add Apple Provider Configuration and Authorize URL Support

**Files:**
- Modify: `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProvider.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProperties.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthState.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthRedirectService.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java`

- [x] **Step 1: Write the failing authorize test**

Add Apple test properties to `@SpringBootTest(properties = { ... })` in `UserOAuthRedirectIntegrationTest`.

```java
"app.oauth.providers.apple.client-id=test.apple.service",
"app.oauth.providers.apple.client-secret=",
"app.oauth.providers.apple.authorization-uri=https://appleid.apple.com/auth/authorize",
"app.oauth.providers.apple.token-uri=https://appleid.apple.com/auth/token",
"app.oauth.providers.apple.user-info-uri=",
"app.oauth.providers.apple.scope=openid,email,name",
"app.oauth.providers.apple.jwks-uri=https://appleid.apple.com/auth/keys",
"app.oauth.providers.apple.response-mode=form_post",
"app.oauth.providers.apple.team-id=TEAM1234567",
"app.oauth.providers.apple.key-id=KEY1234567",
"app.oauth.providers.apple.private-key-base64=dGVzdC1rZXk=",
"app.oauth.providers.apple.client-secret-ttl-days=30"
```

Add this test method:

```java
@Test
@DisplayName("Apple OAuth authorize redirects to Apple with form_post response mode and nonce")
void appleAuthorize_redirectsWithFormPostAndNonce() throws Exception {
    String location = authorizeAndGetProviderLocation("apple");
    Map<String, String> query = queryOf(location);

    assertThat(location).startsWith("https://appleid.apple.com/auth/authorize?");
    assertThat(query).containsEntry("response_type", "code");
    assertThat(query).containsEntry("client_id", "test.apple.service");
    assertThat(query).containsEntry("redirect_uri", "http://localhost:8080/api/auth/oauth/apple/callback");
    assertThat(query).containsEntry("scope", "openid email name");
    assertThat(query).containsEntry("response_mode", "form_post");
    assertThat(query.get("state")).isNotBlank();
    assertThat(query.get("nonce")).isNotBlank();
    assertThat(redis.hasKey("auth:oauth:state:" + query.get("state"))).isTrue();
}
```

Replace the existing helper:

```java
private String authorizeAndGetProviderLocation() throws Exception {
    return authorizeAndGetProviderLocation("google");
}

private String authorizeAndGetProviderLocation(String provider) throws Exception {
    return mockMvc.perform(get("/api/auth/oauth/{provider}/authorize", provider)
                    .param("redirectUri", FRONTEND_CALLBACK))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.LOCATION);
}
```

- [x] **Step 2: Run the failing test**

Run:

```bash
./mvnw -Dtest=UserOAuthRedirectIntegrationTest#appleAuthorize_redirectsWithFormPostAndNonce test
```

Expected: FAIL with `U014` or unsupported provider behavior because `OAuthProvider.APPLE` does not exist yet.

- [x] **Step 3: Add Apple enum value**

Change `OAuthProvider`:

```java
public enum OAuthProvider {
    GOOGLE("google"),
    KAKAO("kakao"),
    NAVER("naver"),
    APPLE("apple");
```

- [x] **Step 4: Extend OAuth provider properties**

Change the nested `Provider` record in `OAuthProperties`:

```java
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
```

Update direct `new OAuthProperties.Provider(...)` calls in tests by adding six trailing values:

```java
null, null, null, null, null, null
```

- [x] **Step 5: Store OAuth nonce in state**

Change `OAuthState`:

```java
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
```

- [x] **Step 6: Add response_mode and nonce to authorize redirect**

Update `OAuthRedirectService.buildAuthorizationRedirect`:

```java
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
```

Add import:

```java
import java.util.UUID;
```

- [x] **Step 7: Add Apple config defaults**

Add under `app.oauth.providers` in `application.yaml`:

```yaml
      apple:
        client-id: ${APPLE_OAUTH_CLIENT_ID:}
        client-secret: ${APPLE_OAUTH_CLIENT_SECRET:}
        authorization-uri: https://appleid.apple.com/auth/authorize
        token-uri: https://appleid.apple.com/auth/token
        user-info-uri:
        jwks-uri: https://appleid.apple.com/auth/keys
        response-mode: form_post
        scope: openid,email,name
        team-id: ${APPLE_OAUTH_TEAM_ID:}
        key-id: ${APPLE_OAUTH_KEY_ID:}
        private-key-base64: ${APPLE_OAUTH_PRIVATE_KEY_BASE64:}
        client-secret-ttl-days: ${APPLE_OAUTH_CLIENT_SECRET_TTL_DAYS:30}
```

- [x] **Step 8: Run the authorize test**

Run:

```bash
./mvnw -Dtest=UserOAuthRedirectIntegrationTest#appleAuthorize_redirectsWithFormPostAndNonce test
```

Expected: PASS.

- [x] **Step 9: Commit Task 1**

```bash
git add src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProvider.java src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthProperties.java src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthState.java src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthRedirectService.java src/main/resources/application.yaml src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java
git commit -m "feat(auth): prepare apple oauth authorize flow"
```

---

### Task 2: Support Apple POST Callback Payload

**Files:**
- Modify: `src/main/java/com/holaclimbing/server/domain/user/OAuthRedirectController.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthAuthorizationCodeRequest.java`
- Modify: `src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthRedirectService.java`
- Test: `src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java`

- [x] **Step 1: Write the failing POST callback integration test**

Extend the fake resolver in `UserOAuthRedirectIntegrationTest` so it has both Google and Apple clients:

```java
return new OAuthProviderClientResolver(List.of(
        fakeGoogleOAuthProviderClient(),
        fakeAppleOAuthProviderClient()
));
```

Add helper methods inside `FakeOAuthProviderConfig`:

```java
private OAuthProviderClient fakeGoogleOAuthProviderClient() {
    return new OAuthProviderClient() {
        @Override
        public OAuthProvider provider() {
            return OAuthProvider.GOOGLE;
        }

        @Override
        public OAuthUserProfile fetchProfile(OAuthAuthorizationCodeRequest request) {
            return switch (request.code()) {
                case "existing-code" -> new OAuthUserProfile(
                        OAuthProvider.GOOGLE,
                        "existing-google-id",
                        "existing-google@hola.com",
                        "Existing Google",
                        "https://example.com/existing.png"
                );
                case "new-code" -> new OAuthUserProfile(
                        OAuthProvider.GOOGLE,
                        "new-google-id",
                        "new-google@hola.com",
                        "New Google",
                        "https://example.com/new.png"
                );
                case "local-email-code" -> new OAuthUserProfile(
                        OAuthProvider.GOOGLE,
                        "local-email-google-id",
                        "local-email@hola.com",
                        "Local Email Google",
                        "https://example.com/local.png"
                );
                default -> throw new AssertionError("Unexpected OAuth code: " + request.code());
            };
        }
    };
}

private OAuthProviderClient fakeAppleOAuthProviderClient() {
    return new OAuthProviderClient() {
        @Override
        public OAuthProvider provider() {
            return OAuthProvider.APPLE;
        }

        @Override
        public OAuthUserProfile fetchProfile(OAuthAuthorizationCodeRequest request) {
            return switch (request.code()) {
                case "apple-new-code" -> {
                    assertThat(request.nonce()).isNotBlank();
                    assertThat(request.providerUserJson()).contains("Apple New");
                    yield new OAuthUserProfile(
                            OAuthProvider.APPLE,
                            "apple-sub-new",
                            "apple-new@hola.com",
                            "Apple New",
                            null
                    );
                }
                default -> throw new AssertionError("Unexpected Apple OAuth code: " + request.code());
            };
        }
    };
}
```

Add this test method:

```java
@Test
@DisplayName("Apple OAuth callback accepts form_post user payload and returns pending signup")
void applePostCallback_acceptsFormPostPayload() throws Exception {
    String authorizeLocation = authorizeAndGetProviderLocation("apple");
    String state = queryOf(authorizeLocation).get("state");

    String frontendLocation = mockMvc.perform(post("/api/auth/oauth/apple/callback")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("code", "apple-new-code")
                    .param("state", state)
                    .param("user", """
                            {"name":{"firstName":"Apple","lastName":"New"},"email":"apple-new@hola.com"}
                            """))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.LOCATION);

    String oauthCode = queryOf(frontendLocation).get("oauthCode");
    assertThat(oauthCode).isNotBlank();

    mockMvc.perform(post("/api/auth/oauth/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
            .andExpect(jsonPath("$.data.signupRequired").value(true))
            .andExpect(jsonPath("$.data.signupToken").isNotEmpty())
            .andExpect(jsonPath("$.data.email").value("apple-new@hola.com"))
            .andExpect(jsonPath("$.data.suggestedNickname").value("Apple New"));
}
```

- [x] **Step 2: Run the failing test**

Run:

```bash
./mvnw -Dtest=UserOAuthRedirectIntegrationTest#applePostCallback_acceptsFormPostPayload test
```

Expected: FAIL with `405 Method Not Allowed` because only GET callback exists.

- [x] **Step 3: Extend authorization code request**

Change `OAuthAuthorizationCodeRequest`:

```java
public record OAuthAuthorizationCodeRequest(
        OAuthProvider provider,
        String code,
        String redirectUri,
        String nonce,
        String providerUserJson
) {
    public OAuthAuthorizationCodeRequest(OAuthProvider provider, String code, String redirectUri) {
        this(provider, code, redirectUri, null, null);
    }
}
```

- [x] **Step 4: Add POST callback endpoint**

Add this method to `OAuthRedirectController`:

```java
@ApiErrorCodes({UNSUPPORTED_OAUTH_PROVIDER, INVALID_OAUTH_STATE, OAUTH_AUTHORIZATION_FAILED, USER_SUSPENDED})
@PostMapping("/{provider}/callback")
public ResponseEntity<Void> callbackPost(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam String state,
                                         @RequestParam(required = false, name = "error") String error,
                                         @RequestParam(required = false, name = "user") String user) {
    return redirect(oauthRedirectService.handleCallback(provider, code, state, error, user));
}
```

- [x] **Step 5: Pass nonce and Apple user JSON through service**

Keep the existing method signature for GET callbacks:

```java
@Transactional
public String handleCallback(String providerValue, String providerCode, String stateToken, String providerError) {
    return handleCallback(providerValue, providerCode, stateToken, providerError, null);
}
```

Add the new overload and use it as the canonical implementation:

```java
@Transactional
public String handleCallback(
        String providerValue,
        String providerCode,
        String stateToken,
        String providerError,
        String providerUserJson
) {
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
            .fetchProfile(new OAuthAuthorizationCodeRequest(
                    provider,
                    providerCode,
                    backendRedirectUri,
                    state.nonce(),
                    providerUserJson
            ));
    OAuthLoginResponse response = resolveLoginResponse(profile);
    String oauthCode = resultStore.issue(response);
    return UriComponentsBuilder.fromUriString(state.redirectUri())
            .queryParam("oauthCode", oauthCode)
            .build()
            .toUriString();
}
```

- [x] **Step 6: Run the POST callback test**

Run:

```bash
./mvnw -Dtest=UserOAuthRedirectIntegrationTest#applePostCallback_acceptsFormPostPayload test
```

Expected: PASS.

- [x] **Step 7: Commit Task 2**

```bash
git add src/main/java/com/holaclimbing/server/domain/user/OAuthRedirectController.java src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthAuthorizationCodeRequest.java src/main/java/com/holaclimbing/server/domain/user/oauth/OAuthRedirectService.java src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java
git commit -m "feat(auth): accept apple oauth form post callback"
```

---

### Task 3: Generate Apple Client Secret JWT

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/user/oauth/ApplePrivateKeyParser.java`
- Create: `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleClientSecretGenerator.java`
- Test: `src/test/java/com/holaclimbing/server/domain/user/oauth/AppleClientSecretGeneratorTest.java`

- [x] **Step 1: Write the failing client secret test**

Create `AppleClientSecretGeneratorTest`:

```java
package com.holaclimbing.server.domain.user.oauth;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class AppleClientSecretGeneratorTest {

    @Test
    void generate_createsEs256JwtWithAppleRequiredClaims() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKeyBase64 = privateKeyAsBase64Pem((ECPrivateKey) keyPair.getPrivate());

        OAuthProperties.Provider provider = new OAuthProperties.Provider(
                "test.apple.service",
                null,
                "https://appleid.apple.com/auth/authorize",
                "https://appleid.apple.com/auth/token",
                null,
                java.util.List.of("openid", "email", "name"),
                "https://appleid.apple.com/auth/keys",
                "form_post",
                "TEAM1234567",
                "KEY1234567",
                privateKeyBase64,
                30
        );

        AppleClientSecretGenerator clientSecretGenerator = new AppleClientSecretGenerator(
                new ApplePrivateKeyParser(),
                Clock.fixed(Instant.parse("2026-06-27T00:00:00Z"), ZoneOffset.UTC)
        );

        String token = clientSecretGenerator.generate(provider);

        var jws = Jwts.parser()
                .verifyWith((ECPublicKey) keyPair.getPublic())
                .build()
                .parseSignedClaims(token);

        assertThat(jws.getHeader().getKeyId()).isEqualTo("KEY1234567");
        assertThat(jws.getPayload().getIssuer()).isEqualTo("TEAM1234567");
        assertThat(jws.getPayload().getSubject()).isEqualTo("test.apple.service");
        assertThat(jws.getPayload().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jws.getPayload().getExpiration().toInstant()).isEqualTo(Instant.parse("2026-07-27T00:00:00Z"));
    }

    private static String privateKeyAsBase64Pem(ECPrivateKey privateKey) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(privateKey.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [x] **Step 2: Run the failing test**

Run:

```bash
./mvnw -Dtest=AppleClientSecretGeneratorTest test
```

Expected: FAIL because `AppleClientSecretGenerator` and `ApplePrivateKeyParser` do not exist.

- [x] **Step 3: Implement Apple private key parsing**

Create `ApplePrivateKeyParser`:

```java
package com.holaclimbing.server.domain.user.oauth;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ApplePrivateKeyParser {

    public PrivateKey parseBase64Pem(String privateKeyBase64) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        try {
            String pem = new String(Base64.getDecoder().decode(privateKeyBase64), StandardCharsets.UTF_8);
            String body = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }
}
```

- [x] **Step 4: Implement Apple client secret generator**

Create `AppleClientSecretGenerator`:

```java
package com.holaclimbing.server.domain.user.oauth;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class AppleClientSecretGenerator {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";

    private final ApplePrivateKeyParser privateKeyParser;
    private final Clock clock;

    public AppleClientSecretGenerator(ApplePrivateKeyParser privateKeyParser) {
        this(privateKeyParser, Clock.systemUTC());
    }

    AppleClientSecretGenerator(ApplePrivateKeyParser privateKeyParser, Clock clock) {
        this.privateKeyParser = privateKeyParser;
        this.clock = clock;
    }

    public String generate(OAuthProperties.Provider provider) {
        if (provider == null
                || isBlank(provider.clientId())
                || isBlank(provider.teamId())
                || isBlank(provider.keyId())
                || isBlank(provider.privateKeyBase64())) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(provider.effectiveClientSecretTtlDays(), ChronoUnit.DAYS);
        PrivateKey privateKey = privateKeyParser.parseBase64Pem(provider.privateKeyBase64());

        return Jwts.builder()
                .header()
                .keyId(provider.keyId())
                .and()
                .issuer(provider.teamId())
                .subject(provider.clientId())
                .audience()
                .add(APPLE_AUDIENCE)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

- [x] **Step 5: Register beans**

Annotate both classes as Spring beans:

```java
@Component
public class ApplePrivateKeyParser {
```

```java
@Component
public class AppleClientSecretGenerator {
    public AppleClientSecretGenerator(ApplePrivateKeyParser privateKeyParser) {
        this(privateKeyParser, Clock.systemUTC());
    }
```

- [x] **Step 6: Run the client secret test**

Run:

```bash
./mvnw -Dtest=AppleClientSecretGeneratorTest test
```

Expected: PASS.

- [x] **Step 7: Commit Task 3**

```bash
git add src/main/java/com/holaclimbing/server/domain/user/oauth/ApplePrivateKeyParser.java src/main/java/com/holaclimbing/server/domain/user/oauth/AppleClientSecretGenerator.java src/test/java/com/holaclimbing/server/domain/user/oauth/AppleClientSecretGeneratorTest.java
git commit -m "feat(auth): generate apple oauth client secret"
```

---

### Task 4: Verify Apple ID Tokens with JWKS

**Files:**
- Create: `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleJwksClient.java`
- Create: `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenClaims.java`
- Create: `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenVerifier.java`
- Test: `src/test/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenVerifierTest.java`

- [x] **Step 1: Write the failing ID token verifier test**

Create `AppleIdTokenVerifierTest` with a local JWKS server:

```java
package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AppleIdTokenVerifierTest {

    private HttpServer server;
    private KeyPair keyPair;
    private String jwksUri;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys", this::handleKeys);
        server.start();
        jwksUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/keys";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verify_validatesSignatureAndRequiredClaims() {
        OAuthProperties.Provider provider = new OAuthProperties.Provider(
                "test.apple.service",
                null,
                "https://appleid.apple.com/auth/authorize",
                "https://appleid.apple.com/auth/token",
                null,
                java.util.List.of("openid", "email", "name"),
                jwksUri,
                "form_post",
                "TEAM1234567",
                "KEY1234567",
                "unused",
                30
        );
        String token = Jwts.builder()
                .header()
                .keyId("apple-rsa-key")
                .and()
                .issuer("https://appleid.apple.com")
                .subject("apple-sub")
                .audience()
                .add("test.apple.service")
                .and()
                .issuedAt(Date.from(Instant.parse("2026-06-27T00:00:00Z")))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .claim("nonce", "nonce-123")
                .claim("email", "apple@hola.com")
                .claim("email_verified", true)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        AppleIdTokenVerifier verifier = new AppleIdTokenVerifier(
                new AppleJwksClient(RestClient.builder(), new ObjectMapper())
        );

        AppleIdTokenClaims claims = verifier.verify(token, provider, "nonce-123");

        assertThat(claims.subject()).isEqualTo("apple-sub");
        assertThat(claims.email()).isEqualTo("apple@hola.com");
        assertThat(claims.emailVerified()).isTrue();
    }

    private void handleKeys(HttpExchange exchange) throws IOException {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        String body = """
                {"keys":[{"kty":"RSA","kid":"apple-rsa-key","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
                """.formatted(base64Url(publicKey.getModulus()), base64Url(publicKey.getPublicExponent()));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

- [x] **Step 2: Run the failing test**

Run:

```bash
./mvnw -Dtest=AppleIdTokenVerifierTest test
```

Expected: FAIL because verifier/JWKS classes do not exist.

- [x] **Step 3: Implement Apple ID token claims record**

Create `AppleIdTokenClaims`:

```java
package com.holaclimbing.server.domain.user.oauth;

public record AppleIdTokenClaims(
        String subject,
        String email,
        boolean emailVerified
) {
}
```

- [x] **Step 4: Implement Apple JWKS client**

Create `AppleJwksClient`:

```java
package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AppleJwksClient {

    private static final long CACHE_TTL_SECONDS = 3600;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, CachedKey> cache = new ConcurrentHashMap<>();

    public AppleJwksClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public PublicKey getKey(String jwksUri, String keyId) {
        CachedKey cached = cache.get(keyId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.key();
        }
        refresh(jwksUri);
        CachedKey refreshed = cache.get(keyId);
        if (refreshed == null) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        return refreshed.key();
    }

    private void refresh(String jwksUri) {
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        try {
            String body = restClient.get().uri(jwksUri).retrieve().body(String.class);
            JsonNode keys = objectMapper.readTree(body).path("keys");
            Instant expiresAt = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
            for (JsonNode key : keys) {
                String kid = key.path("kid").asText(null);
                String n = key.path("n").asText(null);
                String e = key.path("e").asText(null);
                if (kid != null && n != null && e != null) {
                    cache.put(kid, new CachedKey(toPublicKey(n, e), expiresAt));
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    private PublicKey toPublicKey(String n, String e) throws Exception {
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private record CachedKey(PublicKey key, Instant expiresAt) {
    }
}
```

- [x] **Step 5: Implement Apple ID token verifier**

Create `AppleIdTokenVerifier`:

```java
package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;

@Component
public class AppleIdTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final AppleJwksClient jwksClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppleIdTokenVerifier(AppleJwksClient jwksClient) {
        this.jwksClient = jwksClient;
    }

    public AppleIdTokenClaims verify(String idToken, OAuthProperties.Provider provider, String expectedNonce) {
        if (idToken == null || idToken.isBlank() || provider == null) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        try {
            String keyId = keyIdOf(idToken);
            PublicKey publicKey = jwksClient.getKey(provider.jwksUri(), keyId);
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
            if (!claims.getAudience().contains(provider.clientId())) {
                throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
            }
            String actualNonce = claims.get("nonce", String.class);
            if (expectedNonce != null && !expectedNonce.isBlank() && !expectedNonce.equals(actualNonce)) {
                throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
            }
            return new AppleIdTokenClaims(
                    subject,
                    claims.get("email", String.class),
                    emailVerified(claims.get("email_verified"))
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
    }

    private String keyIdOf(String idToken) throws Exception {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        return objectMapper.readTree(headerJson).path("kid").asText();
    }

    private boolean emailVerified(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
```

- [x] **Step 6: Run the verifier test**

Run:

```bash
./mvnw -Dtest=AppleIdTokenVerifierTest test
```

Expected: PASS.

- [x] **Step 7: Commit Task 4**

```bash
git add src/main/java/com/holaclimbing/server/domain/user/oauth/AppleJwksClient.java src/main/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenClaims.java src/main/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenVerifier.java src/test/java/com/holaclimbing/server/domain/user/oauth/AppleIdTokenVerifierTest.java
git commit -m "feat(auth): verify apple id tokens"
```

---

### Task 5: Implement Apple OAuth Provider Client

**Files:**
- Modify: `src/main/java/com/holaclimbing/server/domain/user/oauth/AbstractOAuthProviderClient.java`
- Create: `src/main/java/com/holaclimbing/server/domain/user/oauth/AppleOAuthProviderClient.java`
- Test: `src/test/java/com/holaclimbing/server/domain/user/oauth/AppleOAuthProviderClientTest.java`

- [x] **Step 1: Write the failing Apple provider client test**

Create `AppleOAuthProviderClientTest`. Use the same JWKS helper pattern from `AppleIdTokenVerifierTest`, and add a `/token` handler:

```java
@Test
void fetchProfile_exchangesCodeVerifiesIdTokenAndUsesAppleUserJsonName() {
    OAuthUserProfile profile = client.fetchProfile(new OAuthAuthorizationCodeRequest(
            OAuthProvider.APPLE,
            "provider-code",
            "https://api.hola-climb.app/api/auth/oauth/apple/callback",
            "nonce-123",
            """
            {"name":{"firstName":"Apple","lastName":"Climber"},"email":"apple@hola.com"}
            """
    ));

    assertThat(profile.provider()).isEqualTo(OAuthProvider.APPLE);
    assertThat(profile.providerId()).isEqualTo("apple-sub");
    assertThat(profile.email()).isEqualTo("apple@hola.com");
    assertThat(profile.nickname()).isEqualTo("Apple Climber");
    assertThat(profile.profileImage()).isNull();
}
```

The `/token` handler must assert form fields:

```java
String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
assertThat(requestBody).contains("grant_type=authorization_code");
assertThat(requestBody).contains("client_id=test.apple.service");
assertThat(requestBody).contains("code=provider-code");
assertThat(requestBody).contains("redirect_uri=https%3A%2F%2Fapi.hola-climb.app%2Fapi%2Fauth%2Foauth%2Fapple%2Fcallback");
assertThat(requestBody).contains("client_secret=");
```

Return an Apple-like token response:

```java
respond(exchange, 200, """
        {"access_token":"apple-access","token_type":"Bearer","expires_in":3600,"id_token":"%s"}
        """.formatted(idToken()));
```

- [x] **Step 2: Run the failing test**

Run:

```bash
./mvnw -Dtest=AppleOAuthProviderClientTest test
```

Expected: FAIL because `AppleOAuthProviderClient` does not exist and `AbstractOAuthProviderClient` only returns access tokens.

- [x] **Step 3: Refactor abstract client to expose token response**

In `AbstractOAuthProviderClient`, replace `exchangeCodeForAccessToken` internals with:

```java
protected String exchangeCodeForAccessToken(OAuthAuthorizationCodeRequest request) {
    JsonNode response = exchangeCodeForTokenResponse(request);
    String accessToken = response == null ? null : response.path("access_token").asText(null);
    if (isBlank(accessToken)) {
        throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }
    return accessToken;
}

protected JsonNode exchangeCodeForTokenResponse(OAuthAuthorizationCodeRequest request) {
    OAuthProperties.Provider providerProperties = properties.provider(request.provider());
    if (providerProperties == null || isBlank(providerProperties.clientId()) || isBlank(providerProperties.tokenUri())) {
        throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("client_id", providerProperties.clientId());
    String clientSecret = clientSecretValue(request, providerProperties);
    if (!isBlank(clientSecret)) {
        body.add("client_secret", clientSecret);
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
        return parseJson(request.provider(), "token exchange", responseBody);
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

protected OAuthProperties.Provider providerProperties(OAuthProvider provider) {
    OAuthProperties.Provider providerProperties = properties.provider(provider);
    if (providerProperties == null) {
        throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }
    return providerProperties;
}

protected String clientSecretValue(OAuthAuthorizationCodeRequest request, OAuthProperties.Provider providerProperties) {
    return providerProperties.clientSecret();
}

protected JsonNode parseJson(OAuthProvider provider, String step, String responseBody) {
    ...
}
```

Change `parseJson` from `private` to `protected`.

- [x] **Step 4: Implement Apple provider client**

Create `AppleOAuthProviderClient`:

```java
package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AppleOAuthProviderClient extends AbstractOAuthProviderClient {

    private final AppleClientSecretGenerator clientSecretGenerator;
    private final AppleIdTokenVerifier idTokenVerifier;
    private final ObjectMapper objectMapper;

    public AppleOAuthProviderClient(
            RestClient.Builder builder,
            OAuthProperties properties,
            ObjectMapper objectMapper,
            AppleClientSecretGenerator clientSecretGenerator,
            AppleIdTokenVerifier idTokenVerifier
    ) {
        super(builder, properties, objectMapper);
        this.objectMapper = objectMapper;
        this.clientSecretGenerator = clientSecretGenerator;
        this.idTokenVerifier = idTokenVerifier;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.APPLE;
    }

    @Override
    public OAuthUserProfile fetchProfile(OAuthAuthorizationCodeRequest request) {
        OAuthProperties.Provider providerProperties = providerProperties(provider());
        JsonNode tokenResponse = exchangeCodeForTokenResponse(request);
        String idToken = tokenResponse.path("id_token").asText(null);
        if (idToken == null || idToken.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_FAILED);
        }
        AppleIdTokenClaims claims = idTokenVerifier.verify(idToken, providerProperties, request.nonce());
        AppleUserPayload userPayload = parseUserPayload(request.providerUserJson());
        String email = claims.email() != null ? claims.email() : userPayload.email();
        String nickname = userPayload.fullName();
        return new OAuthUserProfile(
                provider(),
                claims.subject(),
                email,
                nickname,
                null
        );
    }

    @Override
    protected String clientSecretValue(OAuthAuthorizationCodeRequest request, OAuthProperties.Provider providerProperties) {
        return clientSecretGenerator.generate(providerProperties);
    }

    private AppleUserPayload parseUserPayload(String providerUserJson) {
        if (providerUserJson == null || providerUserJson.isBlank()) {
            return new AppleUserPayload(null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(providerUserJson);
            JsonNode name = root.path("name");
            String firstName = name.path("firstName").asText("");
            String lastName = name.path("lastName").asText("");
            String fullName = (firstName + " " + lastName).trim();
            return new AppleUserPayload(
                    root.path("email").asText(null),
                    fullName.isBlank() ? null : fullName
            );
        } catch (Exception e) {
            return new AppleUserPayload(null, null);
        }
    }

    private record AppleUserPayload(String email, String fullName) {
    }
}
```

- [x] **Step 5: Run Apple provider client test**

Run:

```bash
./mvnw -Dtest=AppleOAuthProviderClientTest test
```

Expected: PASS.

- [x] **Step 6: Run existing Google provider test**

Run:

```bash
./mvnw -Dtest=GoogleOAuthProviderClientTest test
```

Expected: PASS. This proves the abstract client refactor did not break existing providers.

- [x] **Step 7: Commit Task 5**

```bash
git add src/main/java/com/holaclimbing/server/domain/user/oauth/AbstractOAuthProviderClient.java src/main/java/com/holaclimbing/server/domain/user/oauth/AppleOAuthProviderClient.java src/test/java/com/holaclimbing/server/domain/user/oauth/AppleOAuthProviderClientTest.java
git commit -m "feat(auth): add apple oauth provider client"
```

---

### Task 6: Cover Apple Login Result and Signup Policy End-to-End

**Files:**
- Modify: `src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java`

- [x] **Step 1: Add existing Apple social user login test**

Replace the fake Apple client switch with these cases:

```java
return switch (request.code()) {
    case "apple-new-code" -> {
        assertThat(request.nonce()).isNotBlank();
        assertThat(request.providerUserJson()).contains("Apple New");
        yield new OAuthUserProfile(
                OAuthProvider.APPLE,
                "apple-sub-new",
                "apple-new@hola.com",
                "Apple New",
                null
        );
    }
    case "apple-existing-code" -> new OAuthUserProfile(
            OAuthProvider.APPLE,
            "apple-sub-existing",
            "apple-existing@hola.com",
            "Apple Existing",
            null
    );
    case "apple-local-email-code" -> new OAuthUserProfile(
            OAuthProvider.APPLE,
            "apple-sub-local-email",
            "local-email@hola.com",
            "Apple Local",
            null
    );
    default -> throw new AssertionError("Unexpected Apple OAuth code: " + request.code());
};
```

Add test:

```java
@Test
@DisplayName("Apple OAuth callback for existing social user returns Hola tokens")
void appleCallback_existingSocialUser_returnsTokenThroughOneTimeCode() throws Exception {
    User existing = User.builder()
            .email("apple-existing@hola.com")
            .emailVerified(true)
            .provider("apple")
            .providerId("apple-sub-existing")
            .nickname("appleexisting")
            .build();
    userMapper.insertOAuth(existing);

    String oauthCode = appleCallbackAndExtractOauthCode("apple-existing-code", null);

    mockMvc.perform(post("/api/auth/oauth/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
            .andExpect(jsonPath("$.data.signupRequired").value(false))
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.token.refreshToken").isNotEmpty());
}
```

Add helper:

```java
private String appleCallbackAndExtractOauthCode(String providerCode, String userJson) throws Exception {
    String authorizeLocation = authorizeAndGetProviderLocation("apple");
    String state = queryOf(authorizeLocation).get("state");

    var request = post("/api/auth/oauth/apple/callback")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("code", providerCode)
            .param("state", state);
    if (userJson != null) {
        request.param("user", userJson);
    }

    String frontendLocation = mockMvc.perform(request)
            .andExpect(status().is3xxRedirection())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.LOCATION);

    assertThat(frontendLocation).startsWith(FRONTEND_CALLBACK + "?");
    String oauthCode = queryOf(frontendLocation).get("oauthCode");
    assertThat(oauthCode).isNotBlank();
    return oauthCode;
}
```

- [x] **Step 2: Add Apple local email conflict test**

Add test:

```java
@Test
@DisplayName("Apple OAuth callback for existing local email returns email-existing status")
void appleCallback_existingLocalEmail_returnsEmailAlreadyExistsStatus() throws Exception {
    User localUser = User.builder()
            .email("local-email@hola.com")
            .passwordHash("hashed-password")
            .emailVerified(true)
            .nickname("localemail")
            .build();
    userMapper.insert(localUser);

    String oauthCode = appleCallbackAndExtractOauthCode("apple-local-email-code", null);

    JsonNode resultData = dataOf(mockMvc.perform(post("/api/auth/oauth/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new OAuthResultRequest(oauthCode))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.data.signupRequired").value(false))
            .andExpect(jsonPath("$.data.email").value("local-email@hola.com")));

    assertThat(resultData.hasNonNull("token")).isFalse();
    assertThat(resultData.hasNonNull("signupToken")).isFalse();
    assertThat(userMapper.findByProvider("apple", "apple-sub-local-email")).isNull();
}
```

- [x] **Step 3: Run integration tests**

Run:

```bash
./mvnw -Dtest=UserOAuthRedirectIntegrationTest test
```

Expected: PASS.

- [x] **Step 4: Commit Task 6**

```bash
git add src/test/java/com/holaclimbing/server/domain/user/UserOAuthRedirectIntegrationTest.java
git commit -m "test(auth): cover apple oauth login policies"
```

---

### Task 7: Update README and Operational Configuration Notes

**Files:**
- Modify: `README.md`

- [x] **Step 1: Update environment variable table**

Add rows near the existing OAuth rows:

```markdown
| `APPLE_OAUTH_CLIENT_ID` | Apple Services ID (`client_id`) | (없음) |
| `APPLE_OAUTH_TEAM_ID` | Apple Developer Team ID (`iss`) | (없음) |
| `APPLE_OAUTH_KEY_ID` | Sign in with Apple private key ID (`kid`) | (없음) |
| `APPLE_OAUTH_PRIVATE_KEY_BASE64` | Apple `.p8` private key PEM text를 base64 인코딩한 값 | (없음) |
| `APPLE_OAUTH_CLIENT_SECRET_TTL_DAYS` | 서버가 동적으로 생성하는 Apple client secret JWT 만료 일수 | `30` |
```

Change the feature list row:

```markdown
| 회원·인증 | 이메일 인증 회원가입, 로그인, 카카오·네이버·구글·애플 OAuth 로그인, JWT 토큰 재발급, 로그아웃, 비밀번호 재설정, 회원 탈퇴 |
```

- [x] **Step 2: Add Apple operating checklist**

Add this paragraph under OAuth operating notes:

```markdown
Apple OAuth는 Apple Developer에서 Services ID를 만들고, Return URL에
`{APP_BASE_URL}/api/auth/oauth/apple/callback`을 등록해야 합니다.
Sign in with Apple private key(`.p8`)는 PEM 원문을 base64로 인코딩해
`APPLE_OAUTH_PRIVATE_KEY_BASE64`에 저장합니다. 운영에서는 `APP_BASE_URL`이
Apple 콘솔에 등록한 HTTPS 백엔드 URL과 정확히 일치해야 합니다.
```

- [x] **Step 3: Commit Task 7**

```bash
git add README.md
git commit -m "docs(auth): document apple oauth configuration"
```

---

### Task 8: Final Verification

**Files:**
- Verify all changed files.

- [x] **Step 1: Run focused OAuth tests**

Run:

```bash
./mvnw -Dtest=UserOAuthRedirectIntegrationTest,GoogleOAuthProviderClientTest,AppleClientSecretGeneratorTest,AppleIdTokenVerifierTest,AppleOAuthProviderClientTest test
```

Expected: build success, 0 failures, 0 errors.

- [x] **Step 2: Run auth regression tests**

Run:

```bash
./mvnw -Dtest=UserAuthIntegrationTest,UserOAuthRedirectIntegrationTest test
```

Expected: build success, 0 failures, 0 errors.

- [x] **Step 3: Run full suite**

Run:

```bash
./mvnw test
```

Expected: build success, 0 failures, 0 errors.

- [x] **Step 4: Inspect final diff**

Run:

```bash
git status --short
git diff --stat
```

Expected: only Apple OAuth implementation, tests, config, and README changes.

- [x] **Step 5: Commit final adjustments if any**

If Task 8 required small fixes after previous commits:

```bash
git add src/main/java/com/holaclimbing/server/domain/user/oauth src/main/java/com/holaclimbing/server/domain/user/OAuthRedirectController.java src/main/resources/application.yaml src/test/java/com/holaclimbing/server/domain/user src/test/java/com/holaclimbing/server/domain/user/oauth README.md
git commit -m "fix(auth): stabilize apple oauth verification"
```

## Self-Review Notes

- Scope is one subsystem: Apple provider support in the existing OAuth redirect flow.
- The existing frontend contract remains unchanged: frontend still receives `oauthCode` and calls `/api/auth/oauth/result`.
- The existing DB schema is reused; no migration is required.
- Apple-specific behavior is isolated in `Apple*` OAuth classes.
- Existing Google/Kakao/Naver behavior is protected by `GoogleOAuthProviderClientTest` and `UserOAuthRedirectIntegrationTest`.
- No JWT tokens are added to browser URLs.
