package com.guesschess.application;

import com.guesschess.domain.game.GameId;

import java.util.Optional;

/**
 * Port : ecrit une fois a la creation de la partie, lu ensuite a chaque action d'un
 * joueur pour retrouver sa couleur a partir de son jeton.
 */
public interface GameAccessRepository {

    void save(GameAccess access);

    Optional<GameAccess> findByToken(PlayerToken token);

    Optional<GameAccess> findByGameId(GameId gameId);
}
