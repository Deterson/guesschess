package com.guesschess.domain.game;

import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;

/**
 * Resolution d'un round (coup reel + devinette). guessedMove est nullable (pas de
 * devinette soumise a temps). movePlayed indique si actualMove a ete applique au
 * plateau (devinette fausse ou absente) ou annule (devinette correcte).
 */
public record RoundResult(
        Color mover,
        Color guesser,
        Move actualMove,
        Move guessedMove,
        boolean movePlayed
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

    static RoundResult played(Color mover, Color guesser, Move actualMove, Move guessedMove) {
        return new RoundResult(mover, guesser, actualMove, guessedMove, true);
    }

    static RoundResult cancelled(Color mover, Color guesser, Move actualMove, Move guessedMove) {
        return new RoundResult(mover, guesser, actualMove, guessedMove, false);
    }
}
