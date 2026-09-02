package com.guesschess.infrastructure.security;

import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Emission des JWT stateless (etape 4 de la roadmap) apres un login OAuth reussi -
 * aucune session n'est stockee cote serveur pour les endpoints REST du contexte
 * "Compte joueur" (le flux WebSocket/PlayerToken existant n'est pas concerne).
 *
 * Emet aussi, depuis l'etape 14, un second type de jeton bien plus court a vivre : le
 * jeton d'inscription en attente (claim "type"="pending_registration"), remis a la
 * place d'un jeton de session normal quand un login OAuth reussi ne correspond a
 * aucun compte existant. Il porte l'identite OAuth verifiee (provider/externalId/
 * email) et l'identite anonyme du navigateur, mais n'autorise rien par lui-meme :
 * RegistrationController est le seul endpoint qui le lit, et seulement pour creer le
 * compte une fois un login valide fourni (voir AccountService.completeRegistration).
 * Un jeton de ce type ne doit jamais etre traite comme un Bearer de session (son sujet
 * n'est pas un UserId valide) - il ne quitte donc jamais le corps de la reponse de
 * redirection OAuth, jamais un en-tete Authorization.
 */
@Component
public class JwtService {

    private static final String PENDING_REGISTRATION_TYPE = "pending_registration";
    private static final Duration PENDING_REGISTRATION_TTL = Duration.ofMinutes(15);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Duration ttl;

    public JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, @Value("${app.jwt.ttl}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
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
        return encode(claims);
    }

    public String generatePendingRegistrationToken(OAuthProvider provider, String externalId, String email, AnonymousId anonymousId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("guesschess")
                .issuedAt(now)
                .expiresAt(now.plus(PENDING_REGISTRATION_TTL))
                .subject(provider.name() + ":" + externalId)
                .claim("type", PENDING_REGISTRATION_TYPE)
                .claim("provider", provider.name())
                .claim("externalId", externalId);
        if (email != null) {
            claims.claim("email", email);
        }
        if (anonymousId != null) {
            claims.claim("anonymousId", anonymousId.toString());
        }
        return encode(claims.build());
    }

    /**
     * @throws IllegalArgumentException si token n'est pas un jeton d'inscription en
     * attente valide (signature/expiration invalide, ou type de jeton different) -
     * l'appelant (RegistrationController) traduit en reponse HTTP.
     */
    public PendingRegistration decodePendingRegistrationToken(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid or expired pending registration token", e);
        }
        if (!PENDING_REGISTRATION_TYPE.equals(jwt.getClaimAsString("type"))) {
            throw new IllegalArgumentException("not a pending registration token");
        }
        String anonymousIdClaim = jwt.getClaimAsString("anonymousId");
        return new PendingRegistration(
                OAuthProvider.valueOf(jwt.getClaimAsString("provider")),
                jwt.getClaimAsString("externalId"),
                jwt.getClaimAsString("email"),
                anonymousIdClaim == null ? null : AnonymousId.fromString(anonymousIdClaim));
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public record PendingRegistration(OAuthProvider provider, String externalId, String email, AnonymousId anonymousId) {
    }
}
