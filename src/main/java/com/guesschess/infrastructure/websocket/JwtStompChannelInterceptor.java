package com.guesschess.infrastructure.websocket;

import com.guesschess.domain.account.UserId;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Resout le compte joueur (etape 6 de la roadmap) a la connexion STOMP a partir d'un
 * header "Authorization: Bearer &lt;jwt&gt;" pose par le client au CONNECT - les
 * navigateurs n'autorisent pas de header custom sur le handshake HTTP WebSocket
 * lui-meme, contrairement au frame CONNECT STOMP qui accepte des headers arbitraires.
 * Un jeton absent ou invalide n'empeche pas la connexion : le joueur reste identifie
 * de facon anonyme (voir AnonymousIdentityHandshakeInterceptor), le jeu restant
 * possible sans compte.
 */
@Component
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public JwtStompChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.CONNECT) {
            UserId userId = resolveUserId(accessor.getFirstNativeHeader("Authorization"));
            if (userId != null && accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put(WebSocketPlayerIdentity.USER_ID_ATTRIBUTE, userId);
            }
        }
        return message;
    }

    private UserId resolveUserId(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            return UserId.fromString(jwt.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
