package com.guesschess.infrastructure.websocket;

import com.guesschess.domain.account.AnonymousId;
import com.guesschess.infrastructure.security.AnonymousIdentityFilter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Recupere l'identite anonyme deja resolue par AnonymousIdentityFilter (attribut pose
 * plus tot sur la meme requete HTTP, la chaine de filtres Spring Security s'executant
 * avant que le handshake WebSocket ne soit traite) et la copie dans les attributs de
 * session WebSocket. Spring STOMP les reporte automatiquement dans
 * simpSessionAttributes a la connexion - voir WebSocketPlayerIdentity.
 */
@Component
public class AnonymousIdentityHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Object anonymousId = servletRequest.getServletRequest().getAttribute(AnonymousIdentityFilter.REQUEST_ATTRIBUTE);
            if (anonymousId instanceof AnonymousId id) {
                attributes.put(WebSocketPlayerIdentity.ANONYMOUS_ID_ATTRIBUTE, id);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}
