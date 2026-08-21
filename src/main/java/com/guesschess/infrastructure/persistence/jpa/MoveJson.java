package com.guesschess.infrastructure.persistence.jpa;

record MoveJson(
        String from,
        String to,
        PieceJson movedPiece,
        PieceJson capturedPiece,
        String type,
        String promotionType
) {
}
