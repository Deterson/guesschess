package com.guesschess.application.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TranspositionTableTest {

    @Test
    void probeReturnsNullWhenNoEntryIsStored() {
        TranspositionTable tt = new TranspositionTable();

        assertNull(tt.probe(1L, 3, -1000, 1000));
    }

    @Test
    void exactEntryIsReusedRegardlessOfTheAlphaBetaWindow() {
        TranspositionTable tt = new TranspositionTable();
        tt.store(1L, 3, 42, TranspositionTable.Bound.EXACT);

        assertEquals(42, tt.probe(1L, 3, -1000, 1000));
        assertEquals(42, tt.probe(1L, 3, 40, 41));
    }

    @Test
    void entryIsIgnoredWhenStoredDepthIsShallowerThanRequested() {
        TranspositionTable tt = new TranspositionTable();
        tt.store(1L, 2, 42, TranspositionTable.Bound.EXACT);

        assertNull(tt.probe(1L, 3, -1000, 1000));
        assertEquals(42, tt.probe(1L, 2, -1000, 1000));
    }

    @Test
    void lowerBoundOnlyCutsWhenItAlreadyReachesBeta() {
        TranspositionTable tt = new TranspositionTable();
        tt.store(1L, 3, 50, TranspositionTable.Bound.LOWERBOUND);

        assertEquals(50, tt.probe(1L, 3, -1000, 40));
        assertNull(tt.probe(1L, 3, -1000, 100));
    }

    @Test
    void upperBoundOnlyCutsWhenItAlreadyReachesAlpha() {
        TranspositionTable tt = new TranspositionTable();
        tt.store(1L, 3, -50, TranspositionTable.Bound.UPPERBOUND);

        assertEquals(-50, tt.probe(1L, 3, -40, 1000));
        assertNull(tt.probe(1L, 3, -100, 1000));
    }

    @Test
    void storingAShallowerDepthNeverOverwritesADeeperEntry() {
        TranspositionTable tt = new TranspositionTable();
        tt.store(1L, 3, 42, TranspositionTable.Bound.EXACT);
        tt.store(1L, 1, 7, TranspositionTable.Bound.EXACT);

        assertEquals(42, tt.probe(1L, 3, -1000, 1000));
    }
}
