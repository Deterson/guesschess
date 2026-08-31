package com.guesschess.application;

import com.guesschess.domain.game.GameId;

/**
 * Leve quand un coup ou une devinette est soumis pour une partie dont les deux
 * couleurs ne sont pas encore liees a un joueur reel (compte ou identite anonyme) -
 * empeche le createur de jouer contre lui-meme tant que personne n'a rejoint (voir
 * GameLifecycleService.isFull).
 */
public class GameNotFullException extends RuntimeException {

    public GameNotFullException(GameId gameId) {
        super("game is not full yet, cannot act: " + gameId);
    }
}
