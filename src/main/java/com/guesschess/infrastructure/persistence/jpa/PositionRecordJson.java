package com.guesschess.infrastructure.persistence.jpa;

/**
 * Miroir de Game.PositionRecord : origin vaut "MOVE" ou "GUESS" (nom de
 * Game.PositionOrigin).
 */
record PositionRecordJson(
        BoardJson board,
        String origin
) {
}
