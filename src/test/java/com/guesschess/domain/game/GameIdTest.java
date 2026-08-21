package com.guesschess.domain.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GameIdTest {

    @Test
    void randomGeneratesDistinctIds() {
        assertNotEquals(GameId.random(), GameId.random());
    }

    @Test
    void fromStringRoundTripsThroughToString() {
        GameId id = GameId.random();
        assertEquals(id, GameId.fromString(id.toString()));
    }

    @Test
    void newGameHasAnId() {
        assertNotEquals(Game.newGame().id(), Game.newGame().id());
    }
}
