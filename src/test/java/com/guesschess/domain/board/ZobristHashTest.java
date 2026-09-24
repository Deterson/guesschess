package com.guesschess.domain.board;

import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ZobristHashTest {

    @Test
    void sameBoardProducesTheSameHashTwice() {
        Board board = Board.initial();

        assertEquals(ZobristHash.hash(board), ZobristHash.hash(board));
    }

    @Test
    void differentSideToMoveProducesADifferentHash() {
        Board white = Board.initial();
        Board black = white.withSideToMove(Color.BLACK);

        assertNotEquals(ZobristHash.hash(white), ZobristHash.hash(black));
    }

    @Test
    void differentCastlingRightsProduceADifferentHash() {
        Board withRights = Board.initial();
        Board withoutRights = withRights.withCastlingRights(CastlingRights.none());

        assertNotEquals(ZobristHash.hash(withRights), ZobristHash.hash(withoutRights));
    }

    @Test
    void differentEnPassantTargetProducesADifferentHash() {
        Board board = Board.initial();
        Board withEnPassant = Board.reconstruct(board.squaresSnapshot(), board.sideToMove(), board.castlingRights(),
                Position.fromAlgebraic("e3"), board.halfmoveClock(), board.fullmoveNumber());

        assertNotEquals(ZobristHash.hash(board), ZobristHash.hash(withEnPassant));
    }

    @Test
    void differentPiecePlacementProducesADifferentHash() {
        Board board = Board.initial();
        Board moved = board.withPiece(Position.fromAlgebraic("e4"), Piece.of(PieceType.PAWN, Color.WHITE));

        assertNotEquals(ZobristHash.hash(board), ZobristHash.hash(moved));
    }

    /**
     * Meme convention que Board.equals/hashCode (isSamePosition) : les compteurs de coups
     * ne distinguent pas deux positions aux echecs, le hash Zobrist doit les ignorer pareil
     * - sinon une table de transposition ne verrait jamais deux fois "la meme" position des
     * que les compteurs different, ce qui arrive a chaque round.
     */
    @Test
    void halfmoveAndFullmoveCountersDoNotAffectTheHash() {
        Board board = Board.initial();
        Board laterInTheGame = Board.reconstruct(board.squaresSnapshot(), board.sideToMove(), board.castlingRights(),
                board.enPassantTarget(), 7, 42);

        assertEquals(ZobristHash.hash(board), ZobristHash.hash(laterInTheGame));
    }
}
