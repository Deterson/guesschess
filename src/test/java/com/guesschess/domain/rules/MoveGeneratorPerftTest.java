package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.CastlingRights;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Perft ("performance test") : compare le nombre de positions atteignables a des
 * profondeurs connues, valeurs de reference standard en programmation d'echecs
 * (chessprogramming.org/Perft_Results). Ces tests couvrent transversalement les
 * deplacements de toutes les pieces, les prises, les echecs et les clouages.
 */
class MoveGeneratorPerftTest {

    @ParameterizedTest(name = "startpos depth {0} -> {1} nodes")
    @CsvSource({
            "1, 20",
            "2, 400",
            "3, 8902",
            "4, 197281"
    })
    void perftFromStartingPosition(int depth, long expectedNodes) {
        Board board = Board.initial();
        assertEquals(expectedNodes, Perft.count(board, Color.WHITE, depth));
    }

    /**
     * "Kiwipete", la position de reference la plus utilisee pour stresser roque,
     * prise en passant et promotions en meme temps.
     * r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1
     */
    @ParameterizedTest(name = "kiwipete depth {0} -> {1} nodes")
    @CsvSource({
            "1, 48",
            "2, 2039",
            "3, 97862"
    })
    void perftFromKiwipetePosition(int depth, long expectedNodes) {
        Board board = kiwipetePosition();
        assertEquals(expectedNodes, Perft.count(board, Color.WHITE, depth));
    }

    @Test
    void startingPositionHasTwentyLegalMoves() {
        Board board = Board.initial();
        assertEquals(20, MoveGenerator.generateLegalMoves(board, Color.WHITE).size());
    }

    private static Board kiwipetePosition() {
        Board board = Board.empty();
        board = place(board, "a8", PieceType.ROOK, Color.BLACK);
        board = place(board, "e8", PieceType.KING, Color.BLACK);
        board = place(board, "h8", PieceType.ROOK, Color.BLACK);
        board = place(board, "a7", PieceType.PAWN, Color.BLACK);
        board = place(board, "c7", PieceType.PAWN, Color.BLACK);
        board = place(board, "d7", PieceType.PAWN, Color.BLACK);
        board = place(board, "e7", PieceType.QUEEN, Color.BLACK);
        board = place(board, "f7", PieceType.PAWN, Color.BLACK);
        board = place(board, "g7", PieceType.BISHOP, Color.BLACK);
        board = place(board, "a6", PieceType.BISHOP, Color.BLACK);
        board = place(board, "b6", PieceType.KNIGHT, Color.BLACK);
        board = place(board, "e6", PieceType.PAWN, Color.BLACK);
        board = place(board, "f6", PieceType.KNIGHT, Color.BLACK);
        board = place(board, "g6", PieceType.PAWN, Color.BLACK);
        board = place(board, "d5", PieceType.PAWN, Color.WHITE);
        board = place(board, "e5", PieceType.KNIGHT, Color.WHITE);
        board = place(board, "b4", PieceType.PAWN, Color.BLACK);
        board = place(board, "e4", PieceType.PAWN, Color.WHITE);
        board = place(board, "c3", PieceType.KNIGHT, Color.WHITE);
        board = place(board, "f3", PieceType.QUEEN, Color.WHITE);
        board = place(board, "h3", PieceType.PAWN, Color.BLACK);
        board = place(board, "a2", PieceType.PAWN, Color.WHITE);
        board = place(board, "b2", PieceType.PAWN, Color.WHITE);
        board = place(board, "c2", PieceType.PAWN, Color.WHITE);
        board = place(board, "d2", PieceType.BISHOP, Color.WHITE);
        board = place(board, "e2", PieceType.BISHOP, Color.WHITE);
        board = place(board, "f2", PieceType.PAWN, Color.WHITE);
        board = place(board, "g2", PieceType.PAWN, Color.WHITE);
        board = place(board, "h2", PieceType.PAWN, Color.WHITE);
        board = place(board, "a1", PieceType.ROOK, Color.WHITE);
        board = place(board, "e1", PieceType.KING, Color.WHITE);
        board = place(board, "h1", PieceType.ROOK, Color.WHITE);
        return board.withSideToMove(Color.WHITE).withCastlingRights(CastlingRights.initial());
    }

    private static Board place(Board board, String algebraic, PieceType type, Color color) {
        return board.withPiece(Position.fromAlgebraic(algebraic), Piece.of(type, color));
    }
}
