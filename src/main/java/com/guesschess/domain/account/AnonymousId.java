package com.guesschess.domain.account;

import java.util.UUID;

/**
 * Identifiant d'une identite anonyme persistante (etape 6 de la roadmap) : porte par
 * un cookie HttpOnly signe cote navigateur, reutilise pour toutes les parties jouees
 * depuis le meme navigateur sans necessiter de compte.
 */
public record AnonymousId(UUID value) {

    public AnonymousId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }

    public static AnonymousId random() {
        return new AnonymousId(UUID.randomUUID());
    }

    public static AnonymousId fromString(String value) {
        return new AnonymousId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
