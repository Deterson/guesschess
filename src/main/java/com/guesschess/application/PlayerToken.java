package com.guesschess.application;

import java.util.UUID;

/**
 * Jeton ephemere identifiant un joueur pour une partie donnee, en l'absence de
 * comptes joueurs (etape 4 de la roadmap). Attribue a la creation de la partie, un
 * par couleur ; quiconque presente le jeton agit pour la couleur correspondante.
 */
public record PlayerToken(UUID value) {

    public PlayerToken {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    public static PlayerToken random() {
        return new PlayerToken(UUID.randomUUID());
    }

    public static PlayerToken fromString(String value) {
        return new PlayerToken(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
