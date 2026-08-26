package com.guesschess.application;

import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.UserId;

/**
 * Identifiant d'un joueur reel derriere une couleur d'une partie (etape 6 de la
 * roadmap) : soit un compte, soit une identite anonyme persistante, jamais les deux.
 * Reference uniquement l'identifiant du contexte "Compte joueur" (pas l'agregat User
 * ni son comportement), un couplage minimal deliberement accepte pour ce lien plutot
 * qu'un partage de modele entre les deux bounded contexts.
 */
public sealed interface PlayerRef {

    record Account(UserId userId) implements PlayerRef {
        public Account {
            if (userId == null) {
                throw new IllegalArgumentException("userId must not be null");
            }
        }
    }

    record Anonymous(AnonymousId anonymousId) implements PlayerRef {
        public Anonymous {
            if (anonymousId == null) {
                throw new IllegalArgumentException("anonymousId must not be null");
            }
        }
    }
}
