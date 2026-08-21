package com.guesschess.domain.account;

/**
 * Identite d'un joueur chez un fournisseur OAuth : externalId est le "sub" (Google)
 * ou le "id" (GitHub) du fournisseur, jamais l'email (non garanti stable/present).
 */
public record OAuthIdentity(OAuthProvider provider, String externalId) {

    public OAuthIdentity {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId must not be blank");
        }
    }
}
