package com.guesschess.application.computer;

import com.guesschess.application.computer.NegamaxSearch.ScoredMove;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MoveGenerator;
import com.guesschess.domain.rules.PositionEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * searchRootWithDeadline (etape 22, tournoi - voir NegamaxTimedAgent) doit renvoyer le
     * meme resultat que searchRoot quand la deadline est large - une deadline qui n'entrave
     * jamais la recherche ne doit rien changer au resultat.
     */
    @Test
    void searchRootWithDeadlineMatchesSearchRootWhenDeadlineIsFar() {
        long farDeadline = System.nanoTime() + 10_000_000_000L;
        List<ScoredMove> withoutDeadline = NegamaxSearch.searchRoot(Board.initial(), 3);
        List<ScoredMove> withDeadline = NegamaxSearch.searchRootWithDeadline(Board.initial(), 3, farDeadline);

        assertEquals(withoutDeadline, withDeadline);
    }

    /**
     * Une deadline deja depassee avant meme de commencer doit interrompre la recherche
     * rapidement (SearchTimeoutException) plutot que d'explorer l'arbre entier - c'est ce
     * qui rend un "budget de temps par coup" (etape 22) reellement borne, contrairement a
     * searchRoot qui n'a aucune notion de deadline.
     */
    @Test
    void searchRootWithDeadlineTimesOutQuicklyWhenTheDeadlineIsAlreadyPast() {
        long pastDeadline = System.nanoTime() - 1_000_000_000L;

        assertTrue(elapsedMillis(() -> assertThrows(NegamaxSearch.SearchTimeoutException.class,
                () -> NegamaxSearch.searchRootWithDeadline(Board.initial(), 6, pastDeadline))) < 500,
                "a search whose deadline is already past should abort almost immediately, not explore depth 6");
    }

    private static long elapsedMillis(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * Meme position que avoidsHangingTheQueenToAVisibleRecapture, mais a profondeur 1 : trop
     * peu profond pour voir la reprise (Txd8) avec une recherche classique - Dxd8 y ressemble a
     * un gain de materiel gratuit. searchRootQuiescent (etape 25, minimax@2) doit prolonger
     * cette branche jusqu'a la reprise et donc l'eviter, contrairement a searchRoot.
     */
    @Test
    void quiescenceSeesThroughARecaptureThatDepthOneMisses() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.KNIGHT, Color.BLACK));

        ScoredMove plainBest = NegamaxSearch.searchRoot(board, 1).get(0);
        assertEquals(Position.fromAlgebraic("d1"), plainBest.move().from());
        assertEquals(Position.fromAlgebraic("d8"), plainBest.move().to(),
                "sanity check: a plain depth-1 search should fall for the recapture, got " + plainBest);

        ScoredMove quiescentBest = NegamaxSearch.searchRootQuiescent(board, 1, new TranspositionTable(), PositionEvaluator::evaluate).get(0);
        boolean capturesTheDefendedKnight = quiescentBest.move().from().equals(Position.fromAlgebraic("d1"))
                && quiescentBest.move().to().equals(Position.fromAlgebraic("d8"));
        assertFalse(capturesTheDefendedKnight, "quiescence should see the recapture and avoid hanging the queen: " + quiescentBest);
    }

    /**
     * Position volontairement depourvue de toute capture possible, quel que soit le coup
     * choisi (rois eloignes, un seul pion) : searchRootQuiescent doit renvoyer exactement le
     * meme resultat que searchRoot quand aucune branche n'atteint jamais de position
     * "bruyante" (stand pat systematique, aucun ecart introduit dans le cas calme). Note :
     * depuis la position de depart, ce n'est PAS vrai a partir de la profondeur 2 - plusieurs
     * des 20 reponses noires possibles (ex. 1.d4 e5, 1.d4 c5) laissent une prise immediate
     * pour les blancs, precisement ce que la quiescence est censee voir contrairement a
     * searchRoot (voir quiescenceSeesThroughARecaptureThatDepthOneMisses).
     */
    @Test
    void quiescentSearchMatchesPlainSearchWhenNoCaptureIsEverReachable() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a4"), Piece.of(PieceType.PAWN, Color.WHITE));

        List<ScoredMove> plain = NegamaxSearch.searchRoot(board, 1);
        List<ScoredMove> quiescent = NegamaxSearch.searchRootQuiescent(board, 1, new TranspositionTable(), PositionEvaluator::evaluate);

        assertEquals(plain, quiescent);
    }

    /** Le mat force reste trouve a travers la recherche de quiescence (meme position que findsForcedMateInOne). */
    @Test
    void quiescentSearchStillFindsForcedMateInOne() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("c6"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("b1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));

        ScoredMove best = NegamaxSearch.searchRootQuiescent(board, 2, new TranspositionTable(), PositionEvaluator::evaluate).get(0);

        assertEquals(Position.fromAlgebraic("b1"), best.move().from());
        assertEquals(Position.fromAlgebraic("b7"), best.move().to());
        assertTrue(NegamaxSearch.isMateScore(best.score()), "expected a mate score, got " + best.score());
    }

    /** Le court-circuit KING_CAPTURE_SCORE (etat degenere propre a guesschess) reste actif a travers searchRootQuiescent. */
    @Test
    void quiescentSearchAlsoHandlesTheKingCaptureShortCircuit() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);

        ScoredMove best = NegamaxSearch.searchRootQuiescent(board, 2, new TranspositionTable(), PositionEvaluator::evaluate).get(0);

        assertEquals(Position.fromAlgebraic("a8"), best.move().from());
        assertEquals(Position.fromAlgebraic("a1"), best.move().to());
        assertTrue(best.move().isCapture() && best.move().capturedPiece().type() == PieceType.KING);
        assertTrue(NegamaxSearch.isMateScore(best.score()), "expected a decisive score, got " + best.score());
    }
}
