package com.guesschess.domain.game;

/**
 * Cadence façon échecs (Fischer) : temps de base par joueur + incrément crédité après
 * chaque coup réel soumis dans les temps (voir Game.submitMove). Absence de TimeControl
 * (null cote Game) = partie par correspondance, sans pendule (étape 12 de la roadmap).
 */
public record TimeControl(long baseMillis, long incrementMillis) {

    public TimeControl {
        if (baseMillis <= 0) {
            throw new IllegalArgumentException("baseMillis must be positive: " + baseMillis);
        }
        if (incrementMillis < 0) {
            throw new IllegalArgumentException("incrementMillis must not be negative: " + incrementMillis);
        }
    }

    /**
     * baseMinutes accepte les fractions (parties bullet, ex. 0.5 = 1/2 minute) - arrondi
     * a la milliseconde la plus proche.
     */
    public static TimeControl of(double baseMinutes, int incrementSeconds) {
        return new TimeControl(Math.round(baseMinutes * 60_000), incrementSeconds * 1_000L);
    }
}
