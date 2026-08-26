package com.guesschess.infrastructure.config;

import com.guesschess.infrastructure.websocket.AnonymousIdentityHandshakeInterceptor;
import com.guesschess.infrastructure.websocket.JwtStompChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * /topic/games/{id} diffuse l'etat public d'une partie a tous les abonnes (aucune
 * devinette en attente n'y transite jamais, cf. GameSnapshot). /user/queue/* porte
 * les reponses/erreurs privees a l'expediteur d'un message.
 *
 * setAllowedOriginPatterns("*") est un placeholder de developpement, a restreindre
 * au domaine reel du frontend au moment du deploiement (etape 7).
 *
 * AnonymousIdentityHandshakeInterceptor et JwtStompChannelInterceptor resolvent
 * l'identite du joueur connecte (etape 6 de la roadmap) - respectivement l'identite
 * anonyme (cookie, au handshake HTTP) et le compte (JWT, au CONNECT STOMP) - et la
 * deposent dans simpSessionAttributes (voir WebSocketPlayerIdentity).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AnonymousIdentityHandshakeInterceptor anonymousIdentityHandshakeInterceptor;
    private final JwtStompChannelInterceptor jwtStompChannelInterceptor;

    public WebSocketConfig(AnonymousIdentityHandshakeInterceptor anonymousIdentityHandshakeInterceptor,
                            JwtStompChannelInterceptor jwtStompChannelInterceptor) {
        this.anonymousIdentityHandshakeInterceptor = anonymousIdentityHandshakeInterceptor;
        this.jwtStompChannelInterceptor = jwtStompChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(anonymousIdentityHandshakeInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtStompChannelInterceptor);
    }
}
