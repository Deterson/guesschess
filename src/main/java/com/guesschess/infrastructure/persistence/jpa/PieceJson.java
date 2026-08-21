package com.guesschess.infrastructure.persistence.jpa;

/**
 * Piece codee en JSON pour la colonne state de games (persistance uniquement).
 */
record PieceJson(String type, String color) {
}
