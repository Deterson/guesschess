package com.guesschess.application.computer;

import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHeuristicsTest {

    private static final Move QUIET_MOVE_1 = Move.normal(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"),
            Piece.of(PieceType.PAWN, Color.WHITE), null);
    private static final Move QUIET_MOVE_2 = Move.normal(Position.fromAlgebraic("g1"), Position.fromAlgebraic("f3"),
            Piece.of(PieceType.KNIGHT, Color.WHITE), null);
    private static final Move QUIET_MOVE_3 = Move.normal(Position.fromAlgebraic("b1"), Position.fromAlgebraic("c3"),
            Piece.of(PieceType.KNIGHT, Color.WHITE), null);

    @Test
    void aMoveThatNeverCausedACutoffIsNotAKiller() {
        SearchHeuristics heuristics = new SearchHeuristics();

        assertFalse(heuristics.isKiller(QUIET_MOVE_1, 3));
    }

    @Test
    void aMoveThatCausedACutoffBecomesAKillerAtThatDepthOnly() {
        SearchHeuristics heuristics = new SearchHeuristics();
        heuristics.recordCutoff(QUIET_MOVE_1, 3);

        assertTrue(heuristics.isKiller(QUIET_MOVE_1, 3));
        assertFalse(heuristics.isKiller(QUIET_MOVE_1, 2), "a killer at depth 3 should not leak into depth 2");
    }

    @Test
    void keepsTheTwoMostRecentKillersAndForgetsTheOldest() {
        SearchHeuristics heuristics = new SearchHeuristics();
        heuristics.recordCutoff(QUIET_MOVE_1, 3);
        heuristics.recordCutoff(QUIET_MOVE_2, 3);
        heuristics.recordCutoff(QUIET_MOVE_3, 3);

        assertTrue(heuristics.isKiller(QUIET_MOVE_3, 3));
        assertTrue(heuristics.isKiller(QUIET_MOVE_2, 3));
        assertFalse(heuristics.isKiller(QUIET_MOVE_1, 3), "the oldest killer should have been evicted");
    }

    @Test
    void historyScoreAccumulatesAcrossRepeatedCutoffsWeightedByDepthSquared() {
        SearchHeuristics heuristics = new SearchHeuristics();

        assertEquals(0, heuristics.historyScore(QUIET_MOVE_1));

        heuristics.recordCutoff(QUIET_MOVE_1, 2);
        assertEquals(4, heuristics.historyScore(QUIET_MOVE_1));

        heuristics.recordCutoff(QUIET_MOVE_1, 3);
        assertEquals(4 + 9, heuristics.historyScore(QUIET_MOVE_1));
    }

    @Test
    void depthBeyondTheTrackedRangeDoesNotCrash() {
        SearchHeuristics heuristics = new SearchHeuristics();

        heuristics.recordCutoff(QUIET_MOVE_1, 500);

        assertTrue(heuristics.isKiller(QUIET_MOVE_1, 500));
    }
}
