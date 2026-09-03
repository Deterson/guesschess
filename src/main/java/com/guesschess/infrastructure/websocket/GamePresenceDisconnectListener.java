package com.guesschess.infrastructure.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Fermeture d'une session WebSocket (onglet ferme, reseau coupe, navigateur ferme -
 * jamais declenche par une action explicite du joueur) : repercute sur
 * GamePresenceService, puis rediffuse l'identite des joueurs si une couleur vient de
 * passer deconnectee (voir GamePresenceService.unregister).
 */
@Component
public class GamePresenceDisconnectListener {

    private final GamePresenceService presenceService;
    private final PlayersBroadcastService playersBroadcastService;

    public GamePresenceDisconnectListener(GamePresenceService presenceService, PlayersBroadcastService playersBroadcastService) {
        this.presenceService = presenceService;
        this.playersBroadcastService = playersBroadcastService;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        presenceService.unregister(sessionId).ifPresent(playersBroadcastService::broadcastPlayers);
    }
}
