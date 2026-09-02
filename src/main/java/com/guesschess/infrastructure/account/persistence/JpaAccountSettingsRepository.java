package com.guesschess.infrastructure.account.persistence;

import com.guesschess.domain.account.AccountSettingKey;
import com.guesschess.domain.account.AccountSettingsRepository;
import com.guesschess.domain.account.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation Postgres/JPA du port AccountSettingsRepository - voir JpaGameRepository
 * pour la raison du @Component plutot que @Repository.
 */
@Component
class JpaAccountSettingsRepository implements AccountSettingsRepository {

    private final SpringDataAccountSettingJpaRepository settings;

    JpaAccountSettingsRepository(SpringDataAccountSettingJpaRepository settings) {
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<AccountSettingKey, String> findByUserId(UserId userId) {
        return settings.findByUserId(userId.value()).stream()
                .collect(Collectors.toMap(e -> AccountSettingKey.valueOf(e.getSettingKey()), AccountSettingEntity::getSettingValue));
    }

    @Override
    @Transactional
    public void upsert(UserId userId, AccountSettingKey key, String value) {
        Instant now = Instant.now();
        settings.findByUserIdAndSettingKey(userId.value(), key.name())
                .ifPresentOrElse(
                        entity -> entity.updateValue(value, now),
                        () -> settings.save(new AccountSettingEntity(UUID.randomUUID(), userId.value(), key.name(), value, now)));
    }
}
