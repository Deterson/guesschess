package com.guesschess.application.computer;

/**
 * L'ordinateur n'est pas utilisable (voir ChessEngine.isAvailable) - leve par
 * GameLifecycleService.createComputerGame avant toute creation de partie, pour ne
 * jamais laisser une partie contre l'ordinateur bloquee sans que celui-ci ne joue
 * jamais son premier coup.
 */
public class ComputerUnavailableException extends RuntimeException {

    public ComputerUnavailableException() {
        super("the computer opponent is not available");
    }
}
