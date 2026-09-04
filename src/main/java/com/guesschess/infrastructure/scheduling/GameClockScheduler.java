package com.guesschess.infrastructure.scheduling;

import com.guesschess.application.GameSnapshot;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameRepository;
import com.guesschess.infrastructure.websocket.GameBroadcastService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Flag-fall (etape 12) : termine au temps les parties dont la pendule active a
 * depasse son temps restant, sans attendre qu'un joueur soumette quoi que ce soit.
 * Le tick lui-meme ne fait qu'un select indexe (findExpiredClockGameIds, voir
 * GameRepository/V10) - le forfait effectif passe par le meme verrouillage
 * (GameRepository.withGame) que les soumissions normales, pour rester correct si un
 * coup arrive au meme instant que l'expiration.
 */
@Component
class GameClockScheduler {

    private final GameRepository gameRepository;
    private final GameBroadcastService broadcastService;

    GameClockScheduler(GameRepository gameRepository, GameBroadcastService broadcastService) {
        this.gameRepository = gameRepository;
        this.broadcastService = broadcastService;
    }

    @Scheduled(fixedDelay = 1000)
    void sweepExpiredClocks() {
        Instant now = Instant.now();
        List<GameId> candidates = gameRepository.findExpiredClockGameIds(now);
        for (GameId gameId : candidates) {
            GameSnapshot snapshot = gameRepository.withGame(gameId, game -> {
                boolean forfeited = game.forfeitOnTimeIfExpired(Instant.now());
                return forfeited ? GameSnapshot.of(game) : null;
            });
            if (snapshot != null) {
                broadcastService.broadcast(snapshot);
            }
        }
    }
}
