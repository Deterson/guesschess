package com.guesschess.application.account;

import com.guesschess.domain.account.OAuthIdentity;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Orchestration du bounded context "Compte joueur" (etape 4 de la roadmap) :
 * find-or-create a chaque login OAuth reussi, idempotent (rappelable sans risque a
 * chaque connexion du meme joueur).
 */
@Service
public class AccountService {

    private final UserRepository userRepository;

    public AccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AccountSnapshot findOrCreateByOAuthIdentity(OAuthProvider provider, String externalId,
                                                         String displayName, String email) {
        User user = userRepository.findByOAuthIdentity(provider, externalId)
                .orElseGet(() -> {
                    User newUser = User.newUser(displayName, email, new OAuthIdentity(provider, externalId));
                    userRepository.insert(newUser);
                    return newUser;
                });
        return toSnapshot(user);
    }

    public AccountSnapshot getById(UserId id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no user found for id: " + id));
        return toSnapshot(user);
    }

    /**
     * Nom d'affichage modifiable par l'utilisateur (etape 8 de la roadmap) - contrainte
     * minimale de 3 caracteres, distinct du futur login unique et immuable (etape 14).
     */
    public AccountSnapshot updateDisplayName(UserId id, String newDisplayName) {
        if (newDisplayName == null || newDisplayName.trim().length() < 3) {
            throw new IllegalArgumentException("displayName must be at least 3 characters");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no user found for id: " + id));
        User updated = new User(user.id(), newDisplayName.trim(), user.email(), user.identities(), user.createdAt());
        userRepository.update(updated);
        return toSnapshot(updated);
    }

    private AccountSnapshot toSnapshot(User user) {
        return new AccountSnapshot(user.id(), user.displayName(), user.email());
    }
}
