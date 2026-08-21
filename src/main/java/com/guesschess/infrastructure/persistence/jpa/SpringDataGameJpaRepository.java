package com.guesschess.infrastructure.persistence.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataGameJpaRepository extends JpaRepository<GameEntity, UUID> {

    /**
     * Verrou pessimiste (SELECT ... FOR UPDATE, tenu pour la duree de la transaction) :
     * equivalent base de la serialisation par cle qu'assurait ConcurrentHashMap.compute()
     * en memoire. Non reentrant, comme l'etait deja compute().
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameEntity g where g.id = :id")
    Optional<GameEntity> findByIdForUpdate(@Param("id") UUID id);
}
