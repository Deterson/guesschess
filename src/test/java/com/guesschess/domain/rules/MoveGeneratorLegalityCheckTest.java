package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * isLegalMove et hasAnyLegalMove doivent s'accorder avec generateLegalMoves, la
 * seule difference etant qu'ils evitent de construire la liste complete.
 */
class MoveGeneratorLegalityCheckTest {

    @Test
    void isLegalMoveAcceptsAKnownLegalMove() {
        Board board = Board.initial();
        Piece pawn = board.pieceAt(Position.fromAlgebraic("e2"));
        Move move = Move.doublePawnPush(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"), pawn);

        assertTrue(MoveGenerator.isLegalMove(board, Color.WHITE, move));
    }

    @Test
    void isLegalMoveRejectsAGeometricallyImpossibleMove() {
        Board board = Board.initial();
        Piece pawn = board.pieceAt(Position.fromAlgebraic("e2"));
        Move move = Move.normal(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e5"), pawn, null);

        assertFalse(MoveGenerator.isLegalMove(board, Color.WHITE, move));
    }

    @Test
    void isLegalMoveRejectsAMoveThatExposesOwnKingToCheck() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e4"), Piece.of(PieceType.BISHOP, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));
        Piece bishop = board.pieceAt(Position.fromAlgebraic("e4"));
        Move move = Move.normal(Position.fromAlgebraic("e4"), Position.fromAlgebraic("d5"), bishop, null);

        assertFalse(MoveGenerator.isLegalMove(board, Color.WHITE, move));
    }

    @Test
    void isLegalMoveRejectsAMoveForThePieceThatIsNotActuallyThere() {
        Board board = Board.initial();
        Piece phantomQueen = Piece.of(PieceType.QUEEN, Color.WHITE);
        Move move = Move.normal(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"), phantomQueen, null);

        assertFalse(MoveGenerator.isLegalMove(board, Color.WHITE, move));
    }

    @Test
    void hasAnyLegalMoveAgreesWithGenerateLegalMovesOnStartingPosition() {
        Board board = Board.initial();
        assertTrue(MoveGenerator.hasAnyLegalMove(board, Color.WHITE));
        assertEquals(!MoveGenerator.generateLegalMoves(board, Color.WHITE).isEmpty(),
                MoveGenerator.hasAnyLegalMove(board, Color.WHITE));
    }

    @Test
    void hasAnyLegalMoveIsFalseInCheckmate() {
        // fool's mate final position: white king e1 mated by the black queen on h4
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f1"), Piece.of(PieceType.BISHOP, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f3"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g4"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h4"), Piece.of(PieceType.QUEEN, Color.BLACK))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));

        assertFalse(MoveGenerator.hasAnyLegalMove(board, Color.WHITE));
        assertTrue(MoveGenerator.generateLegalMoves(board, Color.WHITE).isEmpty());
    }

    @Test
    void hasAnyLegalMoveIsFalseInStalemate() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("f7"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g6"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));

        assertFalse(MoveGenerator.hasAnyLegalMove(board, Color.BLACK));
    }
}
