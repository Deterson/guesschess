package com.guesschess.domain.piece;

public record Piece(PieceType type, Color color) {

    public Piece {
        if (type == null || color == null) {
            throw new IllegalArgumentException("type and color must not be null");
        }
    }

    public static Piece of(PieceType type, Color color) {
        return new Piece(type, color);
    }
}
