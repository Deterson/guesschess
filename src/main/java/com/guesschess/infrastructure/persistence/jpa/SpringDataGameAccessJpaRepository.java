package com.guesschess.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataGameAccessJpaRepository extends JpaRepository<GameAccessEntity, UUID> {

    Optional<GameAccessEntity> findByWhiteTokenOrBlackToken(UUID whiteToken, UUID blackToken);
}
