package com.guesschess.infrastructure.security;

import com.guesschess.domain.account.UserId;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-at-least-32-bytes-long";

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        jwtService = new JwtService(jwtEncoder, Duration.ofHours(1));
    }

    @Test
    void generatesATokenWithSubjectAndDisplayNameClaims() {
        UserId userId = UserId.random();

        String token = jwtService.generateToken(userId, "Alice");
        Jwt decoded = jwtDecoder.decode(token);

        assertEquals(userId.toString(), decoded.getSubject());
        assertEquals("Alice", decoded.getClaimAsString("displayName"));
        assertTrue(decoded.getExpiresAt().isAfter(Instant.now()));
    }
}
