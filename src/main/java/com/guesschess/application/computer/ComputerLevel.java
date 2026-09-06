package com.guesschess.application.computer;

/**
 * Niveau de force de l'ordinateur (etape 15 de la roadmap) - pilote a la fois la force
 * de jeu (voir ChessEngine/StockfishChessEngine) et, pour EASY, la maniere dont
 * l'ordinateur devine (coup plausible plutot que meilleur coup absolu).
 */
public enum ComputerLevel {
    EASY,
    MEDIUM,
    HARD
}
