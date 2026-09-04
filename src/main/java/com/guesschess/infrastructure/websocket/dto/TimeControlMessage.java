package com.guesschess.infrastructure.websocket.dto;

/**
 * Cadence de la partie (etape 12) - null sur GameStateMessage pour une partie par
 * correspondance, sans pendule.
 */
public record TimeControlMessage(long baseMillis, long incrementMillis) {
}
