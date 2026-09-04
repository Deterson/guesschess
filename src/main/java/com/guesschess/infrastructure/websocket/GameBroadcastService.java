package com.guesschess.infrastructure.websocket;

import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameSnapshot;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Diffuse l'etat public d'une partie sur /topic/games/{gameId} - extrait de GameController
 * (etape 12) pour etre reutilisable depuis un appelant qui n'est pas lui-meme un
 * @MessageMapping declenche par un joueur, a savoir GameClockScheduler (flag-fall :
 * la partie se termine sans qu'aucun joueur n'ait rien soumis).
 */
@Component
public class GameBroadcastService {

    private final GameLifecycleService gameLifecycleService;
    private final GameMessageMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;

    public GameBroadcastService(GameLifecycleService gameLifecycleService, GameMessageMapper mapper,
                                 SimpMessagingTemplate messagingTemplate) {
        this.gameLifecycleService = gameLifecycleService;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(GameSnapshot snapshot) {
        boolean full = gameLifecycleService.isFull(snapshot.id());
        messagingTemplate.convertAndSend("/topic/games/" + snapshot.id(), mapper.toGameStateMessage(snapshot, full));
    }
}
