package com.guesschess.application;

import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.PieceType;

/**
 * Intention de coup exprimee par un client (case de depart/arrivee, promotion
 * optionnelle), a resoudre contre les coups legaux de la partie pour obtenir le Move
 * exact du domaine (avec la piece capturee, le type de coup, etc.). Le protocole
 * externe n'a pas a connaitre la structure interne de Move.
 */
public record MoveIntent(Position from, Position to, PieceType promotion) {

    public MoveIntent {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to must not be null");
        }
    }

    public static MoveIntent of(Position from, Position to) {
        return new MoveIntent(from, to, null);
    }

    public static MoveIntent promotingTo(Position from, Position to, PieceType promotion) {
        if (promotion == null) {
            throw new IllegalArgumentException("promotion must not be null");
        }
        return new MoveIntent(from, to, promotion);
    }
}
