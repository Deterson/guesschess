package com.guesschess.infrastructure.websocket;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.domain.game.GameId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Diffuse l'identite des deux joueurs sur /topic/games/{gameId}/players (etape 14),
 * a chaque fois qu'elle change - un adversaire qui rejoint (GameCreationController)
 * ou un joueur anonyme qui se connecte a un compte en pleine partie (relink,
 * OAuthLoginSuccessHandler/RegistrationController). Permet a l'adversaire et aux
 * spectateurs deja connectes de voir le pseudo se mettre a jour sans recharger la
 * page.
 */
@Component
public class PlayersBroadcastService {

    private final GameLifecycleService gameLifecycleService;
    private final GameMessageMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;

    public PlayersBroadcastService(GameLifecycleService gameLifecycleService, GameMessageMapper mapper,
                                    SimpMessagingTemplate messagingTemplate) {
        this.gameLifecycleService = gameLifecycleService;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastPlayers(GameId gameId) {
        gameLifecycleService.findAccess(gameId).ifPresent(this::broadcast);
    }

    private void broadcast(GameAccess access) {
        messagingTemplate.convertAndSend("/topic/games/" + access.gameId() + "/players", mapper.toPlayersMessage(access));
    }
}
