package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.CastlingRights;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.move.MoveType;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialMovesTest {

    @Test
    void kingsideCastleAvailableWhenPathClearAndSafe() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withCastlingRights(CastlingRights.initial());

        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);

        assertTrue(moves.stream().anyMatch(m -> m.type() == MoveType.CASTLE_KINGSIDE));
    }

    @Test
    void castlingUnavailableWhenKingCurrentlyInCheck() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK))
                .withCastlingRights(CastlingRights.initial());

        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);

        assertFalse(moves.stream().anyMatch(m -> m.type() == MoveType.CASTLE_KINGSIDE));
    }

    @Test
    void castlingUnavailableWhenPassingThroughAttackedSquare() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK))
                .withCastlingRights(CastlingRights.initial());

        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);

        assertFalse(moves.stream().anyMatch(m -> m.type() == MoveType.CASTLE_KINGSIDE));
    }

    @Test
    void castlingUnavailableWithoutRights() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withCastlingRights(CastlingRights.none());

        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);

        assertFalse(moves.stream().anyMatch(m -> m.type() == MoveType.CASTLE_KINGSIDE));
    }

    @Test
    void enPassantOnlyAvailableRightAfterDoublePawnPush() {
        Board afterDoublePush = Board.empty()
                .withPiece(Position.fromAlgebraic("e5"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d7"), Piece.of(PieceType.PAWN, Color.BLACK))
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);

        Piece blackPawn = afterDoublePush.pieceAt(Position.fromAlgebraic("d7"));
        Move doublePush = Move.doublePawnPush(Position.fromAlgebraic("d7"), Position.fromAlgebraic("d5"), blackPawn);
        Board withEnPassantAvailable = afterDoublePush.applyMove(doublePush);

        List<Move> whiteMoves = MoveGenerator.generateLegalMoves(withEnPassantAvailable, Color.WHITE);
        assertTrue(whiteMoves.stream().anyMatch(m -> m.type() == MoveType.EN_PASSANT));

        Move quietKingMove = Move.normal(Position.fromAlgebraic("e1"), Position.fromAlgebraic("d1"),
                withEnPassantAvailable.pieceAt(Position.fromAlgebraic("e1")), null);
        Board oneMoveLater = withEnPassantAvailable.applyMove(quietKingMove);

        assertNull(oneMoveLater.enPassantTarget());
    }

    @Test
    void pawnReachingLastRankGeneratesAllFourPromotionChoices() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a7"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));

        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);
        List<Move> promotions = moves.stream().filter(m -> m.type() == MoveType.PROMOTION).toList();

        assertEquals(4, promotions.size());
        assertTrue(promotions.stream().anyMatch(m -> m.promotionType() == PieceType.QUEEN));
        assertTrue(promotions.stream().anyMatch(m -> m.promotionType() == PieceType.ROOK));
        assertTrue(promotions.stream().anyMatch(m -> m.promotionType() == PieceType.BISHOP));
        assertTrue(promotions.stream().anyMatch(m -> m.promotionType() == PieceType.KNIGHT));
    }

    @Test
    void pinnedPieceCannotMoveAndExposeKingToCheck() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e4"), Piece.of(PieceType.BISHOP, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));

        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);

        assertFalse(moves.stream().anyMatch(m -> m.from().equals(Position.fromAlgebraic("e4"))));
    }

    @Test
    void kingCannotMoveIntoCheck() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));

        List<Move> moves = MoveGenerator.generateLegalMoves(board, Color.WHITE);

        assertFalse(moves.stream().anyMatch(m -> m.to().equals(Position.fromAlgebraic("d1"))
                || m.to().equals(Position.fromAlgebraic("d2"))));
    }
}
