package com.guesschess.application;

public class NoSuchLegalMoveException extends RuntimeException {

    public NoSuchLegalMoveException(MoveIntent intent) {
        super("no legal move matches: " + intent);
    }
}
