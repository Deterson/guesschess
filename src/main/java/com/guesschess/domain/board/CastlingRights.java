package com.guesschess.domain.board;

public record CastlingRights(
        boolean whiteKingside,
        boolean whiteQueenside,
        boolean blackKingside,
        boolean blackQueenside
) {

    public static CastlingRights initial() {
        return new CastlingRights(true, true, true, true);
    }

    public static CastlingRights none() {
        return new CastlingRights(false, false, false, false);
    }
}
