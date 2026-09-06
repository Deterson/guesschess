package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Doublure de test pure pour ChessEngine (etape 15) - jamais de vrai process
 * Stockfish. Choisit le premier coup legal par defaut ; alwaysChoose permet de
 * personnaliser la strategie pour un test donne.
 */
public class FakeChessEngine implements ChessEngine {

    private boolean available = true;
    private BiFunction<Board, List<Move>, Move> strategy = (board, legalMoves) -> legalMoves.get(0);

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void alwaysChoose(BiFunction<Board, List<Move>, Move> strategy) {
        this.strategy = strategy;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Move chooseMove(Board board, List<Move> legalMoves, ComputerLevel level) {
        return strategy.apply(board, legalMoves);
    }
}
