package com.guesschess.domain.game;

public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(GameId id) {
        super("no game found for id: " + id);
    }
}
