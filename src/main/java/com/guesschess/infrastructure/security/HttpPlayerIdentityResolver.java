package com.guesschess.infrastructure.security;

import com.guesschess.application.PlayerRef;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.UserId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Equivalent HTTP de WebSocketPlayerIdentity (etape 7 de la roadmap) : un compte JWT
 * l'emporte toujours sur l'identite anonyme de la meme requete. AnonymousIdentityFilter
 * garantit que l'attribut anonyme est toujours present, meme quand aucun compte n'est
 * resolu - il n'y a donc jamais d'identite manquante cote REST, contrairement au flux
 * STOMP ou l'appelant peut ne rien avoir resolu.
 */
@Component
public class HttpPlayerIdentityResolver {

    public PlayerRef resolve(HttpServletRequest request, Jwt jwt) {
        if (jwt != null) {
            return new PlayerRef.Account(UserId.fromString(jwt.getSubject()));
        }
        AnonymousId anonymousId = (AnonymousId) request.getAttribute(AnonymousIdentityFilter.REQUEST_ATTRIBUTE);
        return new PlayerRef.Anonymous(anonymousId);
    }
}
