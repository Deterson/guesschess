package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckDetectorTest {

    @Test
    void initialPositionIsNotInCheck() {
        Board board = Board.initial();
        assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        assertFalse(CheckDetector.isInCheck(board, Color.BLACK));
    }

    @Test
    void rookGivesCheckAlongOpenFile() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.BLACK));

        assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
    }

    @Test
    void pieceBlockingLineOfAttackPreventsCheck() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e4"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.BLACK));

        assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
    }

    @Test
    void knightGivesCheck() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f3"), Piece.of(PieceType.KNIGHT, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));

        assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
    }

    @Test
    void pawnGivesCheckDiagonally() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d2"), Piece.of(PieceType.PAWN, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));

        assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
    }

    @Test
    void bishopGivesCheckDiagonallyAtRange() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.BISHOP, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));

        assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
    }
}
