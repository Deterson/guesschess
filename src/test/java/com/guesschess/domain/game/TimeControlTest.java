package com.guesschess.domain.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeControlTest {

    @Test
    void ofAcceptsFractionalMinutesForBulletTimeControls() {
        assertEquals(30_000L, TimeControl.of(0.5, 0).baseMillis());
        assertEquals(15_000L, TimeControl.of(0.25, 0).baseMillis());
        assertEquals(150_000L, TimeControl.of(2.5, 0).baseMillis());
    }

    @Test
    void ofRejectsAZeroOrNegativeBaseMinutes() {
        assertThrows(IllegalArgumentException.class, () -> TimeControl.of(0, 0));
        assertThrows(IllegalArgumentException.class, () -> TimeControl.of(-1, 0));
    }

    @Test
    void ofRejectsANegativeIncrement() {
        assertThrows(IllegalArgumentException.class, () -> TimeControl.of(5, -1));
    }
}
