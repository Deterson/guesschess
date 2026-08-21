package com.guesschess.infrastructure.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataOAuthIdentityJpaRepository extends JpaRepository<OAuthIdentityEntity, UUID> {

    Optional<OAuthIdentityEntity> findByProviderAndExternalId(String provider, String externalId);

    List<OAuthIdentityEntity> findByUserId(UUID userId);
}
