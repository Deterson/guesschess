package com.guesschess.domain.notation;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Notation SAN (etape 10 de la roadmap, brique reutilisee par le PGGN) : coup de
 * pion, capture, developpement de piece, desambiguisation (fichier / rang / case
 * complete), roque, prise en passant, promotion, et suffixes +/# - uniquement sur un
 * coup reellement joue, conformement a CLAUDE.md.
 */
class SanGeneratorTest {

    @Test
    void pawnDoublePush() {
        Board before = Board.initial();
        Move move = findMove(before, Color.WHITE, "e2", "e4");
        Board after = before.applyMove(move);

        assertEquals("e4", SanGenerator.toSan(before, move, after));
    }

    @Test
    void knightDevelopment() {
        Board before = Board.initial();
        Move move = findMove(before, Color.WHITE, "g1", "f3");
        Board after = before.applyMove(move);

        assertEquals("Nf3", SanGenerator.toSan(before, move, after));
    }

    @Test
    void pawnCapture() {
        Board before = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("e4"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d5"), Piece.of(PieceType.PAWN, Color.BLACK));
        Move move = findMove(before, Color.WHITE, "e4", "d5");
        Board after = before.applyMove(move);

        assertEquals("exd5", SanGenerator.toSan(before, move, after));
    }

    @Test
    void enPassantCaptureUsesPlainCaptureNotation() {
        Board board = Board.initial();
        board = applyMove(board, Color.WHITE, "e2", "e4");
        board = applyMove(board, Color.BLACK, "h7", "h6");
        board = applyMove(board, Color.WHITE, "e4", "e5");
        board = applyMove(board, Color.BLACK, "d7", "d5");

        Board before = board;
        Move move = findMove(before, Color.WHITE, "e5", "d6");
        Board after = before.applyMove(move);

        assertEquals("exd6", SanGenerator.toSan(before, move, after));
    }

    @Test
    void promotion() {
        Board before = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("c5"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("e7"), Piece.of(PieceType.PAWN, Color.WHITE));
        List<Move> promotions = MoveGenerator.generateLegalMoves(before, Color.WHITE).stream()
                .filter(m -> m.to().equals(Position.fromAlgebraic("e8")) && m.promotionType() == PieceType.QUEEN)
                .toList();
        Move move = promotions.get(0);
        Board after = before.applyMove(move);

        assertEquals("e8=Q", SanGenerator.toSan(before, move, after));
    }

    @Test
    void kingsideCastling() {
        Board board = Board.initial();
        board = applyMove(board, Color.WHITE, "g1", "f3");
        board = applyMove(board, Color.BLACK, "g8", "f6");
        board = applyMove(board, Color.WHITE, "g2", "g3");
        board = applyMove(board, Color.BLACK, "g7", "g6");
        board = applyMove(board, Color.WHITE, "f1", "g2");
        board = applyMove(board, Color.BLACK, "f8", "g7");

        Board before = board;
        Move move = findMove(before, Color.WHITE, "e1", "g1");
        Board after = before.applyMove(move);

        assertEquals("O-O", SanGenerator.toSan(before, move, after));
    }

    @Test
    void checkSuffix() {
        Board board = Board.initial();
        board = applyMove(board, Color.WHITE, "e2", "e4");
        board = applyMove(board, Color.BLACK, "d7", "d5");

        Board before = board;
        Move move = findMove(before, Color.WHITE, "f1", "b5");
        Board after = before.applyMove(move);

        assertEquals("Bb5+", SanGenerator.toSan(before, move, after));
    }

    @Test
    void checkmateSuffix() {
        Board board = Board.initial();
        board = applyMove(board, Color.WHITE, "f2", "f3");
        board = applyMove(board, Color.BLACK, "e7", "e5");
        board = applyMove(board, Color.WHITE, "g2", "g4");

        Board before = board;
        Move move = findMove(before, Color.BLACK, "d8", "h4");
        Board after = before.applyMove(move);

        assertEquals("Qh4#", SanGenerator.toSan(before, move, after));
    }

    @Test
    void kingCaptureSuffix() {
        Board before = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e5"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));
        Move move = findMove(before, Color.WHITE, "e5", "e8");
        Board after = before.applyMove(move);

        assertEquals("Rxe8#", SanGenerator.toSan(before, move, after));
    }

    @Test
    void disambiguationByFileWhenBothCandidatesShareTheTargetRankPath() {
        Board before = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("b1"), Piece.of(PieceType.KNIGHT, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f3"), Piece.of(PieceType.KNIGHT, Color.WHITE));

        Move fromB1 = findMove(before, Color.WHITE, "b1", "d2");
        Board afterB1 = before.applyMove(fromB1);
        assertEquals("Nbd2", SanGenerator.toSan(before, fromB1, afterB1));

        Move fromF3 = findMove(before, Color.WHITE, "f3", "d2");
        Board afterF3 = before.applyMove(fromF3);
        assertEquals("Nfd2", SanGenerator.toSan(before, fromF3, afterF3));
    }

    @Test
    void disambiguationByRankWhenCandidatesShareTheSameFile() {
        Board before = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h6"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.WHITE));

        Move fromA1 = findMove(before, Color.WHITE, "a1", "a5");
        Board afterA1 = before.applyMove(fromA1);
        assertEquals("R1a5", SanGenerator.toSan(before, fromA1, afterA1));
    }

    @Test
    void disambiguationByFullSquareWhenFileAndRankAreBothAmbiguous() {
        Board before = Board.empty()
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("c2"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.QUEEN, Color.WHITE));

        Move move = findMove(before, Color.WHITE, "d1", "d4");
        Board after = before.applyMove(move);

        assertEquals("Qd1d4", SanGenerator.toSan(before, move, after));
    }

    private static Board applyMove(Board board, Color color, String from, String to) {
        return board.applyMove(findMove(board, color, from, to));
    }

    private static Move findMove(Board board, Color color, String from, String to) {
        Position fromPos = Position.fromAlgebraic(from);
        Position toPos = Position.fromAlgebraic(to);
        return MoveGenerator.generateLegalMoves(board, color).stream()
                .filter(m -> m.from().equals(fromPos) && m.to().equals(toPos))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no legal move " + from + "-" + to));
    }
}
