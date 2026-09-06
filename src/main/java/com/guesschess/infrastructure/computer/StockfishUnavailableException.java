package com.guesschess.infrastructure.computer;

/**
 * Le binaire Stockfish n'est pas configure (STOCKFISH_PATH absent) ou n'a pas pu etre
 * lance - degrade uniquement la creation d'une partie contre l'ordinateur (voir
 * GameCreationController), sans empecher le reste de l'application de demarrer/
 * fonctionner (contrairement aux variables d'environnement obligatoires, voir
 * CLAUDE.md).
 */
public class StockfishUnavailableException extends RuntimeException {

    public StockfishUnavailableException(String message) {
        super(message);
    }

    public StockfishUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
