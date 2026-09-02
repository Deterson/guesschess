package com.guesschess.infrastructure.account.persistence;

import com.guesschess.domain.account.OAuthIdentity;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation Postgres/JPA du port UserRepository (etape 4 de la roadmap).
 *
 * @Component plutot que @Repository, voir JpaGameRepository pour la raison
 * (traduction automatique d'exceptions non-JPA par Spring).
 */
@Component
class JpaUserRepository implements UserRepository {

    private final SpringDataUserJpaRepository users;
    private final SpringDataOAuthIdentityJpaRepository identities;

    JpaUserRepository(SpringDataUserJpaRepository users, SpringDataOAuthIdentityJpaRepository identities) {
        this.users = users;
        this.identities = identities;
    }

    @Override
    @Transactional
    public void insert(User user) {
        Instant now = Instant.now();
        users.save(new UserEntity(user.id().value(), user.displayName(), user.login(), user.bio(), user.email(), now, now));
        for (OAuthIdentity identity : user.identities()) {
            identities.save(new OAuthIdentityEntity(
                    UUID.randomUUID(), user.id().value(), identity.provider().name(), identity.externalId(), now));
        }
    }

    @Override
    @Transactional
    public void update(User user) {
        UserEntity entity = users.findById(user.id().value())
                .orElseThrow(() -> new IllegalArgumentException("no user found for id: " + user.id()));
        entity.applyChanges(user.displayName(), user.login(), user.bio(), Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLoginIgnoreCase(String login) {
        return users.existsByLoginIgnoreCase(login);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByOAuthIdentity(OAuthProvider provider, String externalId) {
        return identities.findByProviderAndExternalId(provider.name(), externalId)
                .flatMap(identity -> users.findById(identity.getUserId()))
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return users.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByLoginIgnoreCase(String login) {
        return users.findByLoginIgnoreCase(login).map(this::toDomain);
    }

    private User toDomain(UserEntity entity) {
        List<OAuthIdentity> userIdentities = identities.findByUserId(entity.getId()).stream()
                .map(e -> new OAuthIdentity(OAuthProvider.valueOf(e.getProvider()), e.getExternalId()))
                .toList();
        return new User(new UserId(entity.getId()), entity.getDisplayName(), entity.getLogin(), entity.getBio(),
                entity.getEmail(), userIdentities, entity.getCreatedAt());
    }
}
