package com.guesschess.domain.account;

import java.util.Map;

/**
 * Port (au sens hexagonal) : l'implementation vit dans l'infrastructure.
 */
public interface AccountSettingsRepository {

    Map<AccountSettingKey, String> findByUserId(UserId userId);

    void upsert(UserId userId, AccountSettingKey key, String value);
}
