package com.guesschess.domain.game;

import java.util.UUID;

public record GameId(UUID value) {

    public GameId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    public static GameId random() {
        return new GameId(UUID.randomUUID());
    }

    public static GameId fromString(String value) {
        return new GameId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
