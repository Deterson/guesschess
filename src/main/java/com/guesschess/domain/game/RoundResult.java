package com.guesschess.domain.game;

import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;

/**
 * Resolution d'un round (coup reel + devinette). guessedMove est nullable (pas de
 * devinette soumise a temps). movePlayed() indique si actualMove a ete applique au
 * plateau (devinette fausse ou absente) ou annule (devinette correcte) - derive de
 * guessedCorrectly() plutot que stocke separement, les deux etant toujours l'exact
 * inverse l'un de l'autre (Game.resolveRound n'a qu'un seul point de construction).
 */
public record RoundResult(
        Color mover,
        Color guesser,
        Move actualMove,
        Move guessedMove
) {

    public RoundResult {
        if (mover == null || guesser == null || actualMove == null) {
            throw new IllegalArgumentException("mover, guesser and actualMove must not be null");
        }
        if (mover == guesser) {
            throw new IllegalArgumentException("mover and guesser must be different colors");
        }
    }

    public boolean guessedCorrectly() {
        return actualMove.equals(guessedMove);
    }

    public boolean movePlayed() {
        return !guessedCorrectly();
    }

    static RoundResult played(Color mover, Color guesser, Move actualMove, Move guessedMove) {
        return new RoundResult(mover, guesser, actualMove, guessedMove);
    }

    static RoundResult cancelled(Color mover, Color guesser, Move actualMove, Move guessedMove) {
        return new RoundResult(mover, guesser, actualMove, guessedMove);
    }
}
