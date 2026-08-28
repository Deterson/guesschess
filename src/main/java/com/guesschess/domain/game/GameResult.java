package com.guesschess.domain.game;

import com.guesschess.domain.piece.Color;

/**
 * winner est null en cas de nulle.
 */
public record GameResult(Color winner, GameResultCause cause) {

    public GameResult {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        boolean isDraw = cause == GameResultCause.STALEMATE
                || cause == GameResultCause.DRAW_FIFTY_MOVE_RULE
                || cause == GameResultCause.DRAW_THREEFOLD_REPETITION
                || cause == GameResultCause.DRAW_THREE_GUESS_REPETITION
                || cause == GameResultCause.DRAW_INSUFFICIENT_MATERIAL;
        if (isDraw && winner != null) {
            throw new IllegalArgumentException("winner must be null for cause " + cause);
        }
        if (!isDraw && winner == null) {
            throw new IllegalArgumentException("winner is required for cause " + cause);
        }
    }

    public static GameResult win(Color winner, GameResultCause cause) {
        return new GameResult(winner, cause);
    }

    public static GameResult draw(GameResultCause cause) {
        return new GameResult(null, cause);
    }

    public boolean isDraw() {
        return winner == null;
    }
}
