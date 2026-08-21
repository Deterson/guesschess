package com.guesschess.infrastructure.account.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_identities")
class OAuthIdentityEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthIdentityEntity() {
        // JPA
    }

    OAuthIdentityEntity(UUID id, UUID userId, String provider, String externalId, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.externalId = externalId;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getProvider() {
        return provider;
    }

    String getExternalId() {
        return externalId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
