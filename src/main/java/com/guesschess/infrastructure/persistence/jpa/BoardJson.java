package com.guesschess.infrastructure.persistence.jpa;

import java.util.List;

/**
 * squares a 64 elements (index = rank * 8 + file, comme Board.squaresSnapshot()),
 * elements null autorises pour les cases vides.
 */
record BoardJson(
        List<PieceJson> squares,
        String sideToMove,
        CastlingRightsJson castlingRights,
        String enPassantTarget,
        int halfmoveClock,
        int fullmoveNumber
) {
}
