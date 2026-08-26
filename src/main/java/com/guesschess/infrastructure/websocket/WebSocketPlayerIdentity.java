package com.guesschess.infrastructure.websocket;

import com.guesschess.application.PlayerRef;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.UserId;

import java.util.Map;

/**
 * Cles des attributs de session STOMP portant l'identite resolue a la connexion
 * (etape 6 de la roadmap) : ANONYMOUS_ID_ATTRIBUTE pose par
 * AnonymousIdentityHandshakeInterceptor au handshake HTTP, USER_ID_ATTRIBUTE pose par
 * JwtStompChannelInterceptor au CONNECT STOMP. Un compte, quand present, l'emporte
 * toujours sur l'identite anonyme de la meme connexion.
 */
public final class WebSocketPlayerIdentity {

    public static final String USER_ID_ATTRIBUTE = "userId";
    public static final String ANONYMOUS_ID_ATTRIBUTE = "anonymousId";

    private WebSocketPlayerIdentity() {
    }

    public static PlayerRef resolve(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            return null;
        }
        if (sessionAttributes.get(USER_ID_ATTRIBUTE) instanceof UserId userId) {
            return new PlayerRef.Account(userId);
        }
        if (sessionAttributes.get(ANONYMOUS_ID_ATTRIBUTE) instanceof AnonymousId anonymousId) {
            return new PlayerRef.Anonymous(anonymousId);
        }
        return null;
    }
}
