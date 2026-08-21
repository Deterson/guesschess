package com.guesschess.domain.account;

import java.util.Optional;

/**
 * Port (au sens hexagonal) : l'implementation vit dans l'infrastructure.
 */
public interface UserRepository {

    void insert(User user);

    Optional<User> findByOAuthIdentity(OAuthProvider provider, String externalId);

    Optional<User> findById(UserId id);
}
