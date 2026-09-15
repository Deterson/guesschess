package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionEvaluatorTest {

    @Test
    void startingPositionIsPerfectlySymmetric() {
        assertEquals(0, PositionEvaluator.evaluate(Board.initial()));
    }

    @Test
    void anExtraQueenIsWorthClosePositiveScore() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE));

        int score = PositionEvaluator.evaluate(board);

        assertTrue(score > 800, "expected a strongly positive score for white, got " + score);
    }

    @Test
    void theSideMissingAQueenIsPenalisedSymmetrically() {
        Board whiteUp = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE));
        Board blackUp = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.QUEEN, Color.BLACK));

        assertEquals(PositionEvaluator.evaluate(whiteUp), -PositionEvaluator.evaluate(blackUp));
    }

    @Test
    void aCentralPawnIsWorthMoreThanARimPawn() {
        Board centralPawn = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d4"), Piece.of(PieceType.PAWN, Color.WHITE));
        Board rimPawn = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a4"), Piece.of(PieceType.PAWN, Color.WHITE));

        assertTrue(PositionEvaluator.evaluate(centralPawn) > PositionEvaluator.evaluate(rimPawn));
    }
}
