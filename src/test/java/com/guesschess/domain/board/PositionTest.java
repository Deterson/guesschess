package com.guesschess.domain.board;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionTest {

    @ParameterizedTest
    @CsvSource({
            "a1, 0, 0",
            "h1, 7, 0",
            "a8, 0, 7",
            "h8, 7, 7",
            "e4, 4, 3"
    })
    void convertsAlgebraicToFileRank(String algebraic, int expectedFile, int expectedRank) {
        Position position = Position.fromAlgebraic(algebraic);
        assertEquals(expectedFile, position.file());
        assertEquals(expectedRank, position.rank());
    }

    @ParameterizedTest
    @CsvSource({"0,0,a1", "7,0,h1", "0,7,a8", "7,7,h8", "4,3,e4"})
    void convertsFileRankToAlgebraic(int file, int rank, String expected) {
        assertEquals(expected, Position.of(file, rank).toAlgebraic());
    }

    @Test
    void rejectsOutOfBoundsCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> Position.of(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> Position.of(8, 0));
        assertThrows(IllegalArgumentException.class, () -> Position.of(0, -1));
        assertThrows(IllegalArgumentException.class, () -> Position.of(0, 8));
    }

    @Test
    void canTranslateReflectsBoardBounds() {
        Position a1 = Position.of(0, 0);
        assertFalse(a1.canTranslate(-1, 0));
        assertTrue(a1.canTranslate(1, 1));
    }
}
