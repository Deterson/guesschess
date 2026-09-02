package com.guesschess.infrastructure.account.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_settings")
class AccountSettingEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "setting_key", nullable = false)
    private String settingKey;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountSettingEntity() {
        // JPA
    }

    AccountSettingEntity(UUID id, UUID userId, String settingKey, String settingValue, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getSettingKey() {
        return settingKey;
    }

    String getSettingValue() {
        return settingValue;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Entite managee : le dirty-checking JPA repercute ce changement au flush/commit,
     * pas besoin d'un save() explicite (voir JpaAccountSettingsRepository.upsert).
     */
    void updateValue(String settingValue, Instant updatedAt) {
        this.settingValue = settingValue;
        this.updatedAt = updatedAt;
    }
}
