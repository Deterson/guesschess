package com.guesschess.domain.account;

import java.util.Optional;

/**
 * Port (au sens hexagonal) : l'implementation vit dans l'infrastructure.
 */
public interface UserRepository {

    void insert(User user);

    void update(User user);

    Optional<User> findByOAuthIdentity(OAuthProvider provider, String externalId);

    Optional<User> findById(UserId id);

    /**
     * Resolution d'un profil public par login (etape 15) - insensible a la casse,
     * comme existsByLoginIgnoreCase ci-dessous.
     */
    Optional<User> findByLoginIgnoreCase(String login);

    /**
     * Verification d'unicite du login (etape 14), insensible a la casse - voir
     * AccountService.validateLogin. Le veritable garde-fou reste l'index unique sur
     * lower(login) en base (V9) ; cette methode n'evite qu'une erreur peu conviviale
     * dans le cas courant, une course entre deux inscriptions simultanees reste
     * possible et acceptable a cette echelle.
     */
    boolean existsByLoginIgnoreCase(String login);
}
