package com.guesschess.infrastructure.account.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataUserJpaRepository extends JpaRepository<UserEntity, UUID> {
}
