package com.guesschess.application;

import com.guesschess.domain.piece.Color;

/**
 * Leve quand un jeton tente une action reservee a l'autre role du round : soumettre
 * un coup alors qu'on n'est pas le joueur au trait, ou une devinette alors qu'on
 * l'est.
 */
public class WrongTurnException extends RuntimeException {

    public WrongTurnException(Color expected, Color actual) {
        super("expected color " + expected + " for this action, but token belongs to " + actual);
    }
}
