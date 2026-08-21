package com.guesschess.infrastructure.security;

import com.guesschess.domain.account.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Emission des JWT stateless (etape 4 de la roadmap) apres un login OAuth reussi -
 * aucune session n'est stockee cote serveur pour les endpoints REST du contexte
 * "Compte joueur" (le flux WebSocket/PlayerToken existant n'est pas concerne).
 */
@Component
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final Duration ttl;

    public JwtService(JwtEncoder jwtEncoder, @Value("${app.jwt.ttl}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.ttl = ttl;
    }

    public String generateToken(UserId userId, String displayName) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("guesschess")
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(userId.toString())
                .claim("displayName", displayName)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
