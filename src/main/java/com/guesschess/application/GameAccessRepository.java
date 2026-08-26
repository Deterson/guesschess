package com.guesschess.application;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;

import java.util.Optional;

/**
 * Port : ecrit une fois a la creation de la partie, lu ensuite a chaque action d'un
 * joueur pour retrouver sa couleur a partir de son jeton.
 */
public interface GameAccessRepository {

    void save(GameAccess access);

    Optional<GameAccess> findByToken(PlayerToken token);

    Optional<GameAccess> findByGameId(GameId gameId);

    /**
     * Lie ref a color pour gameId si cette couleur n'est pas deja liee (etape 6) -
     * aucun effet sinon, y compris si ref differe du lien deja pose : le lien est
     * immuable une fois etabli (voir GameAccess.withPlayerLinked).
     */
    void linkPlayer(GameId gameId, Color color, PlayerRef ref);
}
