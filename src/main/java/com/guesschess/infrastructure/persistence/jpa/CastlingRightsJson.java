package com.guesschess.infrastructure.persistence.jpa;

record CastlingRightsJson(boolean whiteKingside, boolean whiteQueenside, boolean blackKingside, boolean blackQueenside) {
}
