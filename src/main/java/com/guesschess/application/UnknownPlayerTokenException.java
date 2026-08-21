package com.guesschess.application;

public class UnknownPlayerTokenException extends RuntimeException {

    public UnknownPlayerTokenException(PlayerToken token) {
        super("no game access found for token: " + token);
    }
}
