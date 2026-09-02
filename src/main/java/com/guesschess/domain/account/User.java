package com.guesschess.domain.account;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate root du bounded context "Compte joueur" (etape 4 de la roadmap), separe
 * du contexte "Partie" : aucun lien avec Game/PlayerToken pour l'instant, l'historique
 * de matchs par compte est une fonctionnalite future. email est nullable (GitHub ne le
 * garantit pas) : l'identifiant unique reste toujours (provider, externalId), jamais
 * l'email.
 *
 * login (etape 14) est le pseudonyme unique et immuable une fois pose - nullable
 * uniquement pour representer un compte historique cree avant cette etape, qui doit
 * en choisir un a sa prochaine connexion (voir AccountService.setLoginForExistingAccount)
 * avant de pouvoir faire quoi que ce soit d'autre ; un compte cree via
 * AccountService.completeRegistration a toujours un login des sa creation. bio est
 * non-null (chaine vide par defaut), modifiable sans contrainte d'immuabilite.
 */
public record User(UserId id, String displayName, String login, String bio, String email,
                    List<OAuthIdentity> identities, Instant createdAt) {

    public User {
        if (id == null || createdAt == null) {
            throw new IllegalArgumentException("id and createdAt must not be null");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (bio == null) {
            throw new IllegalArgumentException("bio must not be null");
        }
        if (identities == null || identities.isEmpty()) {
            throw new IllegalArgumentException("identities must not be empty");
        }
        identities = List.copyOf(identities);
    }

    public static User newUser(String login, String email, OAuthIdentity firstIdentity) {
        return new User(UserId.random(), login, login, "", email, List.of(firstIdentity), Instant.now());
    }
}
