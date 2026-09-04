package com.guesschess.infrastructure.web.dto;

/**
 * Cadence choisie a la creation (etape 12) - absent/null sur CreateGameHttpRequest =
 * partie par correspondance, sans pendule. baseMinutes accepte les fractions (parties
 * bullet, ex. 0.5) - voir TimeControl.of.
 */
public record TimeControlHttpRequest(double baseMinutes, int incrementSeconds) {
}
