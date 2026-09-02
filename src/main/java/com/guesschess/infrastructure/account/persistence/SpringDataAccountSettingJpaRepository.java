package com.guesschess.infrastructure.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAccountSettingJpaRepository extends JpaRepository<AccountSettingEntity, UUID> {

    List<AccountSettingEntity> findByUserId(UUID userId);

    Optional<AccountSettingEntity> findByUserIdAndSettingKey(UUID userId, String settingKey);
}
