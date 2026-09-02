package com.guesschess.infrastructure.account.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserEntity {

    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "login")
    private String login;

    @Column(name = "bio", nullable = false)
    private String bio;

    @Column(name = "email")
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
        // JPA
    }

    UserEntity(UUID id, String displayName, String login, String bio, String email, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.login = login;
        this.bio = bio;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getDisplayName() {
        return displayName;
    }

    String getLogin() {
        return login;
    }

    String getBio() {
        return bio;
    }

    String getEmail() {
        return email;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Entite managee : le dirty-checking JPA repercute ce changement au flush/commit,
     * pas besoin d'un save() explicite (voir JpaUserRepository.update).
     */
    void applyChanges(String displayName, String login, String bio, Instant updatedAt) {
        this.displayName = displayName;
        this.login = login;
        this.bio = bio;
        this.updatedAt = updatedAt;
    }
}
