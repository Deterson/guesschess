package com.guesschess.infrastructure.config;

import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
