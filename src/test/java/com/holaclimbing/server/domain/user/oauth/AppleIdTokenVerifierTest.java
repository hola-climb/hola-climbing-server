package com.holaclimbing.server.domain.user.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleIdTokenVerifierTest {

    private static final Instant FIXED_ISSUED_AT = Instant.parse("2026-06-27T00:00:00Z");
    private static final String KEY_ID = "apple-rsa-key";
    private static final String NONCE = "nonce-123";

    private HttpServer server;
    private KeyPair keyPair;
    private OAuthProperties.Provider provider;
    private AppleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = generateRsaKeyPair();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys", exchange -> {
            byte[] response = jwksBody((RSAPublicKey) keyPair.getPublic()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        provider = appleProvider("http://127.0.0.1:" + server.getAddress().getPort() + "/keys");
        verifier = new AppleIdTokenVerifier(new AppleJwksClient(RestClient.builder(), new ObjectMapper()));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verify_validIdTokenReturnsAppleClaims() {
        String token = appleIdToken(provider.clientId(), NONCE);

        AppleIdTokenClaims claims = verifier.verify(token, provider, NONCE);

        assertThat(claims).isEqualTo(new AppleIdTokenClaims("apple-sub", "apple@hola.com", true));
    }

    @Test
    void verify_nonceMismatchThrowsAuthorizationFailure() {
        String token = appleIdToken(provider.clientId(), NONCE);

        assertAuthorizationFailure(() -> verifier.verify(token, provider, "different-nonce"));
    }

    @Test
    void verify_missingExpectedNonceThrowsAuthorizationFailure() {
        String token = appleIdToken(provider.clientId(), NONCE);

        assertAuthorizationFailure(() -> verifier.verify(token, provider, null));
        assertAuthorizationFailure(() -> verifier.verify(token, provider, " "));
    }

    @Test
    void verify_missingTokenNonceThrowsAuthorizationFailure() {
        String token = appleIdTokenWithoutNonce(provider.clientId());

        assertAuthorizationFailure(() -> verifier.verify(token, provider, NONCE));
    }

    @Test
    void verify_blankTokenNonceThrowsAuthorizationFailure() {
        String token = appleIdToken(provider.clientId(), " ");

        assertAuthorizationFailure(() -> verifier.verify(token, provider, NONCE));
    }

    @Test
    void verify_booleanFalseEmailVerifiedThrowsAuthorizationFailure() {
        String token = appleIdToken(provider.clientId(), NONCE, false);

        assertAuthorizationFailure(() -> verifier.verify(token, provider, NONCE));
    }

    @Test
    void verify_stringFalseEmailVerifiedThrowsAuthorizationFailure() {
        String token = appleIdToken(provider.clientId(), NONCE, "false");

        assertAuthorizationFailure(() -> verifier.verify(token, provider, NONCE));
    }

    @Test
    void verify_missingEmailVerifiedThrowsAuthorizationFailure() {
        String token = appleIdTokenWithoutEmailVerified(provider.clientId(), NONCE);

        assertAuthorizationFailure(() -> verifier.verify(token, provider, NONCE));
    }

    @Test
    void verify_uppercaseStringEmailVerifiedThrowsAuthorizationFailure() {
        String uppercaseToken = appleIdToken(provider.clientId(), NONCE, "TRUE");
        String titleCaseToken = appleIdToken(provider.clientId(), NONCE, "True");

        assertAuthorizationFailure(() -> verifier.verify(uppercaseToken, provider, NONCE));
        assertAuthorizationFailure(() -> verifier.verify(titleCaseToken, provider, NONCE));
    }

    private String appleIdToken(String audience, String nonce) {
        return appleIdToken(audience, nonce, true);
    }

    private String appleIdToken(String audience, String nonce, Object emailVerified) {
        return baseAppleIdToken(audience)
                .header().keyId(KEY_ID).and()
                .claim("nonce", nonce)
                .claim("email_verified", emailVerified)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String appleIdTokenWithoutEmailVerified(String audience, String nonce) {
        return baseAppleIdToken(audience)
                .header().keyId(KEY_ID).and()
                .claim("nonce", nonce)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String appleIdTokenWithoutNonce(String audience) {
        return baseAppleIdToken(audience)
                .header().keyId(KEY_ID).and()
                .claim("email_verified", true)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private io.jsonwebtoken.JwtBuilder baseAppleIdToken(String audience) {
        return Jwts.builder()
                .issuer("https://appleid.apple.com")
                .subject("apple-sub")
                .audience().add(audience).and()
                .issuedAt(Date.from(FIXED_ISSUED_AT))
                .expiration(Date.from(Instant.parse("2030-06-27T00:00:00Z")))
                .claim("email", "apple@hola.com");
    }

    private static void assertAuthorizationFailure(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.OAUTH_AUTHORIZATION_FAILED));
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwksBody(RSAPublicKey publicKey) {
        return """
                {"keys":[{"kty":"RSA","kid":"apple-rsa-key","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
                """.formatted(base64Url(publicKey.getModulus().toByteArray()), base64Url(publicKey.getPublicExponent().toByteArray()));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(stripLeadingZero(bytes));
    }

    private static byte[] stripLeadingZero(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }

    private static OAuthProperties.Provider appleProvider(String jwksUri) {
        return new OAuthProperties.Provider(
                "com.hola.app",
                null,
                "https://appleid.apple.com/auth/authorize",
                "https://appleid.apple.com/auth/token",
                null,
                List.of("name", "email"),
                jwksUri,
                "form_post",
                "TEAMID1234",
                "KEYID1234",
                "private-key-base64",
                30
        );
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call() throws Exception;
    }
}
