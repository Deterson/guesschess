package com.guesschess.domain.game;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MaterialEvaluator;
import com.guesschess.domain.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root : encapsule le plateau, l'historique des coups et le resultat d'une
 * partie, et garantit qu'aucun coup illegal ne peut etre joue une fois la partie terminee.
 * Ne connait pas la mecanique de devinette (etape 2 de la roadmap).
 */
public final class Game {

    private static final int FIFTY_MOVE_HALFMOVE_LIMIT = 100;
    private static final int REPETITION_LIMIT = 3;

    private Board board;
    private final List<Board> positionHistory = new ArrayList<>();
    private final List<Move> moveHistory = new ArrayList<>();
    private GameStatus status = GameStatus.ONGOING;
    private GameResult result;

    private Game(Board initialBoard) {
        this.board = initialBoard;
        this.positionHistory.add(initialBoard);
    }

    public static Game newGame() {
        return new Game(Board.initial());
    }

    public static Game fromPosition(Board board) {
        return new Game(board);
    }

    public Board board() {
        return board;
    }

    public Color sideToMove() {
        return board.sideToMove();
    }

    public GameStatus status() {
        return status;
    }

    public GameResult result() {
        return result;
    }

    public List<Move> moveHistory() {
        return Collections.unmodifiableList(moveHistory);
    }

    public boolean isInCheck() {
        return CheckDetector.isInCheck(board, sideToMove());
    }

    public List<Move> legalMoves() {
        if (status != GameStatus.ONGOING) {
            return List.of();
        }
        return MoveGenerator.generateLegalMoves(board, sideToMove());
    }

    public void makeMove(Move move) {
        if (status != GameStatus.ONGOING) {
            throw new IllegalStateException("game is already finished: " + result);
        }
        if (!MoveGenerator.isLegalMove(board, sideToMove(), move)) {
            throw new IllegalArgumentException("illegal move: " + move);
        }

        board = board.applyMove(move);
        moveHistory.add(move);
        positionHistory.add(board);

        resolveGameEnd(move);
    }

    private void resolveGameEnd(Move justPlayed) {
        Color mover = justPlayed.movedPiece().color();
        Color nextToMove = board.sideToMove();
        boolean nextHasLegalMoves = MoveGenerator.hasAnyLegalMove(board, nextToMove);

        if (!nextHasLegalMoves) {
            if (CheckDetector.isInCheck(board, nextToMove)) {
                finish(GameResult.win(mover, GameResultCause.CHECKMATE));
            } else {
                finish(GameResult.draw(GameResultCause.STALEMATE));
            }
            return;
        }
        if (board.halfmoveClock() >= FIFTY_MOVE_HALFMOVE_LIMIT) {
            finish(GameResult.draw(GameResultCause.DRAW_FIFTY_MOVE_RULE));
            return;
        }
        if (countOccurrences(board) >= REPETITION_LIMIT) {
            finish(GameResult.draw(GameResultCause.DRAW_THREEFOLD_REPETITION));
            return;
        }
        if (MaterialEvaluator.isInsufficientMaterial(board)) {
            finish(GameResult.draw(GameResultCause.DRAW_INSUFFICIENT_MATERIAL));
        }
    }

    private void finish(GameResult gameResult) {
        this.status = GameStatus.FINISHED;
        this.result = gameResult;
    }

    private int countOccurrences(Board position) {
        int count = 0;
        for (Board past : positionHistory) {
            if (past.isSamePosition(position)) {
                count++;
            }
        }
        return count;
    }
}
