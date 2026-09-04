package com.guesschess.domain.game;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Port (au sens hexagonal) : l'implementation vit dans l'infrastructure. Game etant
 * mutable, withGame garantit un acces exclusif a une partie donnee le temps de
 * l'action (lecture ou mutation), pour rester correct sous acces concurrents
 * (connexions WebSocket simultanees sur la meme partie).
 */
public interface GameRepository {

    /**
     * Enregistre une toute nouvelle partie. L'id de la partie doit etre inedit.
     */
    void insert(Game game);

    /**
     * Execute action avec un acces exclusif a la partie identifiee par id.
     *
     * @throws GameNotFoundException si aucune partie ne correspond a id
     */
    <T> T withGame(GameId id, Function<Game, T> action);

    /**
     * Ids des parties ONGOING dont la pendule active a depasse deadline, utilise par le
     * scheduler de flag-fall (etape 12) pour ne balayer que les parties concernees sans
     * desincapsuler l'etat de chaque partie en cours a chaque tick.
     */
    List<GameId> findExpiredClockGameIds(Instant deadline);
}
