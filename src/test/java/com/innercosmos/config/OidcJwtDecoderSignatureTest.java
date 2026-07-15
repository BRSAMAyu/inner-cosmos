package com.innercosmos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcJwtDecoderSignatureTest {
    private HttpServer server;
    private KeyPair trustedKey;
    private OidcProperties properties;

    @BeforeEach
    void serveTrustedJwkSet() throws Exception {
        trustedKey = keyPair();
        RSAKey publicJwk = new RSAKey.Builder((RSAPublicKey) trustedKey.getPublic())
                .keyID("trusted-key").build();
        byte[] body = new ObjectMapper().writeValueAsBytes(
                Map.of("keys", List.of(publicJwk.toPublicJWK().toJSONObject())));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        properties = new OidcProperties();
        properties.setIssuerUri("https://identity.example/");
        properties.setAudience("inner-cosmos-api");
        properties.setJwkSetUri("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void decoderVerifiesSignatureIssuerAudienceAndLifetime() throws Exception {
        JwtDecoder decoder = new OidcSecurityConfiguration().oidcJwtDecoder(properties);
        var decoded = decoder.decode(signed(trustedKey, "trusted-key", "inner-cosmos-api"));
        assertThat(decoded.getSubject()).isEqualTo("subject-123");
    }

    @Test
    void decoderRejectsTokenSignedByUntrustedKey() throws Exception {
        JwtDecoder decoder = new OidcSecurityConfiguration().oidcJwtDecoder(properties);
        assertThatThrownBy(() -> decoder.decode(signed(keyPair(), "trusted-key", "inner-cosmos-api")))
                .isInstanceOf(JwtException.class);
    }

    private static String signed(KeyPair keyPair, String keyId, String audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://identity.example/")
                .subject("subject-123")
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
