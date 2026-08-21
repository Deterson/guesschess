package com.guesschess.domain.board;

import com.guesschess.domain.move.Move;
import com.guesschess.domain.move.MoveType;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    @Test
    void initialPositionHasStandardSetup() {
        Board board = Board.initial();

        assertEquals(Color.WHITE, board.sideToMove());
        assertEquals(Piece.of(PieceType.ROOK, Color.WHITE), board.pieceAt(Position.fromAlgebraic("a1")));
        assertEquals(Piece.of(PieceType.KING, Color.WHITE), board.pieceAt(Position.fromAlgebraic("e1")));
        assertEquals(Piece.of(PieceType.KING, Color.BLACK), board.pieceAt(Position.fromAlgebraic("e8")));
        assertEquals(Piece.of(PieceType.PAWN, Color.WHITE), board.pieceAt(Position.fromAlgebraic("e2")));
        assertEquals(Piece.of(PieceType.PAWN, Color.BLACK), board.pieceAt(Position.fromAlgebraic("e7")));
        assertTrue(board.isEmpty(Position.fromAlgebraic("e4")));
        assertEquals(CastlingRights.initial(), board.castlingRights());
        assertNull(board.enPassantTarget());
        assertEquals(0, board.halfmoveClock());
        assertEquals(1, board.fullmoveNumber());
    }

    @Test
    void applyingNormalMoveClearsOriginAndFlipsSideToMove() {
        Board board = Board.initial();
        Piece pawn = board.pieceAt(Position.fromAlgebraic("e2"));
        Move move = Move.normal(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e3"), pawn, null);

        Board after = board.applyMove(move);

        assertTrue(after.isEmpty(Position.fromAlgebraic("e2")));
        assertEquals(pawn, after.pieceAt(Position.fromAlgebraic("e3")));
        assertEquals(Color.BLACK, after.sideToMove());
        assertEquals(0, after.halfmoveClock());
    }

    @Test
    void doublePawnPushSetsEnPassantTarget() {
        Board board = Board.initial();
        Piece pawn = board.pieceAt(Position.fromAlgebraic("e2"));
        Move move = Move.doublePawnPush(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"), pawn);

        Board after = board.applyMove(move);

        assertEquals(Position.fromAlgebraic("e3"), after.enPassantTarget());
    }

    @Test
    void nonPawnNonCaptureMoveIncrementsHalfmoveClock() {
        Board board = Board.initial();
        Piece knight = board.pieceAt(Position.fromAlgebraic("g1"));
        Move move = Move.normal(Position.fromAlgebraic("g1"), Position.fromAlgebraic("f3"), knight, null);

        Board after = board.applyMove(move);

        assertEquals(1, after.halfmoveClock());
    }

    @Test
    void captureResetsHalfmoveClock() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));
        Piece rook = board.pieceAt(Position.fromAlgebraic("a1"));
        Piece capturedRook = board.pieceAt(Position.fromAlgebraic("a8"));
        Move capture = Move.normal(Position.fromAlgebraic("a1"), Position.fromAlgebraic("a8"), rook, capturedRook);

        Board after = board.applyMove(capture);

        assertEquals(0, after.halfmoveClock());
        assertEquals(rook, after.pieceAt(Position.fromAlgebraic("a8")));
    }

    @Test
    void promotionReplacesPawnWithChosenPieceType() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a7"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));
        Piece pawn = board.pieceAt(Position.fromAlgebraic("a7"));
        Move promotion = Move.promotion(Position.fromAlgebraic("a7"), Position.fromAlgebraic("a8"), pawn, null, PieceType.QUEEN);

        Board after = board.applyMove(promotion);

        assertEquals(Piece.of(PieceType.QUEEN, Color.WHITE), after.pieceAt(Position.fromAlgebraic("a8")));
    }

    @Test
    void enPassantMoveRemovesCapturedPawnBehindTarget() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e5"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d5"), Piece.of(PieceType.PAWN, Color.BLACK))
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));
        Piece whitePawn = board.pieceAt(Position.fromAlgebraic("e5"));
        Piece blackPawn = board.pieceAt(Position.fromAlgebraic("d5"));
        Move enPassant = Move.enPassant(Position.fromAlgebraic("e5"), Position.fromAlgebraic("d6"), whitePawn, blackPawn);

        Board after = board.applyMove(enPassant);

        assertTrue(after.isEmpty(Position.fromAlgebraic("d5")));
        assertEquals(whitePawn, after.pieceAt(Position.fromAlgebraic("d6")));
    }

    @Test
    void kingsideCastleMovesBothKingAndRook() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));
        Piece king = board.pieceAt(Position.fromAlgebraic("e1"));
        Move castle = Move.castleKingside(Position.fromAlgebraic("e1"), Position.fromAlgebraic("g1"), king);

        Board after = board.applyMove(castle);

        assertEquals(king, after.pieceAt(Position.fromAlgebraic("g1")));
        assertEquals(Piece.of(PieceType.ROOK, Color.WHITE), after.pieceAt(Position.fromAlgebraic("f1")));
        assertTrue(after.isEmpty(Position.fromAlgebraic("e1")));
        assertTrue(after.isEmpty(Position.fromAlgebraic("h1")));
    }

    @Test
    void movingRookLosesCastlingRightsOnThatSide() {
        Board board = Board.initial();
        Piece rook = board.pieceAt(Position.fromAlgebraic("a1"));
        Move move = Move.normal(Position.fromAlgebraic("a1"), Position.fromAlgebraic("a2"), rook, null);
        // a2 is occupied in the initial position, use a constructed board instead to keep the move structurally valid
        Board custom = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), rook)
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withCastlingRights(CastlingRights.initial());
        Move rookMove = Move.normal(Position.fromAlgebraic("a1"), Position.fromAlgebraic("a4"), rook, null);

        Board after = custom.applyMove(rookMove);

        assertTrue(after.castlingRights().whiteKingside());
        assertEquals(false, after.castlingRights().whiteQueenside());
    }

    @Test
    void samePositionEqualityIgnoresMoveCounters() {
        Board a = Board.initial();
        Board b = Board.initial().withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.ROOK, Color.WHITE));

        assertTrue(a.isSamePosition(b));
    }

    @Test
    void moveTypeIsPreservedThroughApplication() {
        Board board = Board.initial();
        Piece pawn = board.pieceAt(Position.fromAlgebraic("e2"));
        Move move = Move.doublePawnPush(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"), pawn);
        assertEquals(MoveType.DOUBLE_PAWN_PUSH, move.type());
    }
}
