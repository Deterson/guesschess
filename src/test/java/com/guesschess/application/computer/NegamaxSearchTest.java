package com.guesschess.application.computer;

import com.guesschess.application.computer.NegamaxSearch.ScoredMove;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NegamaxSearchTest {

    @Test
    void searchesAllTwentyStartingMovesSortedBestFirst() {
        List<ScoredMove> results = NegamaxSearch.searchRoot(Board.initial(), 2);

        assertEquals(20, results.size());
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).score() >= results.get(i).score(),
                    "results must be sorted best-first: " + results);
        }
    }

    /**
     * Qb1-b7# : reine en b1, roi blanc en c6 (defend b7), roi noir seul dans le coin a8.
     * Seul coup qui mate en un (Qb8+ existe aussi mais n'est pas defendu, Kxb8 refute) -
     * verifie que la recherche prefere un mat force a tout le reste, meme a faible
     * profondeur.
     */
    @Test
    void findsForcedMateInOne() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("c6"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("b1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));

        List<ScoredMove> results = NegamaxSearch.searchRoot(board, 2);
        ScoredMove best = results.get(0);

        assertEquals(Position.fromAlgebraic("b1"), best.move().from());
        assertEquals(Position.fromAlgebraic("b7"), best.move().to());
        assertTrue(NegamaxSearch.isMateScore(best.score()), "expected a mate score, got " + best.score());

        Board afterBestMove = board.applyMove(best.move());
        assertTrue(CheckDetector.isInCheck(afterBestMove, Color.BLACK));
        assertFalse(MoveGenerator.hasAnyLegalMove(afterBestMove, Color.BLACK), "expected checkmate after " + best.move());
    }

    /**
     * Dame blanche en d1 peut prendre un cavalier noir en d8, mais celui-ci est defendu
     * par une tour noire en a8 (rangee 8 degagee) : Dxd8 gagne un cavalier puis perd la
     * dame (Txd8), un echange tres defavorable. La recherche doit voir cette suite a
     * trois demi-coups et preferer un autre coup.
     */
    @Test
    void avoidsHangingTheQueenToAVisibleRecapture() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.KNIGHT, Color.BLACK));

        List<ScoredMove> results = NegamaxSearch.searchRoot(board, 3);
        ScoredMove best = results.get(0);

        boolean capturesTheDefendedKnight = best.move().from().equals(Position.fromAlgebraic("d1"))
                && best.move().to().equals(Position.fromAlgebraic("d8"));
        assertFalse(capturesTheDefendedKnight, "should not hang the queen for a knight: " + best);
        assertTrue(best.score() > -300, "should not accept losing the queen for a knight, score=" + best.score());
    }

    /**
     * Meme position que ci-dessus mais sans la tour a8 : le cavalier d8 est cette fois
     * gratuit, la recherche doit le prendre.
     */
    @Test
    void capturesAnUndefendedPieceForFree() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.ROOK, Color.BLACK));

        List<ScoredMove> results = NegamaxSearch.searchRoot(board, 2);
        ScoredMove best = results.get(0);

        assertEquals(Position.fromAlgebraic("d1"), best.move().from());
        assertEquals(Position.fromAlgebraic("d8"), best.move().to());
        assertTrue(best.score() > 400, "expected a large material gain, score=" + best.score());
    }

    /**
     * Etat degenere propre a guesschess (jamais atteignable en echecs classiques) : un
     * round annule a laisse le roi blanc en echec non resolu (voir Game.resolveRound),
     * donnant aux noirs Ra8xa1 parmi leurs coups legaux - une capture de roi. Sans le
     * court-circuit de NegamaxSearch (voir KING_CAPTURE_SCORE), explorer ce coup produit
     * un plateau sans roi que CheckDetector.findKing ne sait pas interpreter et leve une
     * IllegalStateException des la recursion suivante.
     */
    @Test
    void capturingAKingIsAnImmediateWinInsteadOfCrashing() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);

        List<ScoredMove> results = NegamaxSearch.searchRoot(board, 2);
        ScoredMove best = results.get(0);

        assertEquals(Position.fromAlgebraic("a8"), best.move().from());
        assertEquals(Position.fromAlgebraic("a1"), best.move().to());
        assertTrue(best.move().isCapture() && best.move().capturedPiece().type() == PieceType.KING);
        assertTrue(NegamaxSearch.isMateScore(best.score()), "expected a decisive score, got " + best.score());
    }
}
