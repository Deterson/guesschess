package com.guesschess.domain.account;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate root du bounded context "Compte joueur" (etape 4 de la roadmap), separe
 * du contexte "Partie" : aucun lien avec Game/PlayerToken pour l'instant, l'historique
 * de matchs par compte est une fonctionnalite future. email est nullable (GitHub ne le
 * garantit pas) : l'identifiant unique reste toujours (provider, externalId), jamais
 * l'email.
 */
public record User(UserId id, String displayName, String email, List<OAuthIdentity> identities, Instant createdAt) {

    public User {
        if (id == null || createdAt == null) {
            throw new IllegalArgumentException("id and createdAt must not be null");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (identities == null || identities.isEmpty()) {
            throw new IllegalArgumentException("identities must not be empty");
        }
        identities = List.copyOf(identities);
    }

    public static User newUser(String displayName, String email, OAuthIdentity firstIdentity) {
        return new User(UserId.random(), displayName, email, List.of(firstIdentity), Instant.now());
    }
}
