package com.guesschess.domain.game;

public enum GameResultCause {
    CHECKMATE,
    STALEMATE,
    DRAW_FIFTY_MOVE_RULE,
    DRAW_THREEFOLD_REPETITION,
    DRAW_INSUFFICIENT_MATERIAL
}
