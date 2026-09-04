package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.game.GameStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    /**
     * Ids des parties en cours dont la pendule active a depasse deadline (etape 12) -
     * interroge par index (clock_deadline_at, voir V10) plutot que de desincapsuler le
     * JSONB de chaque partie ONGOING a chaque tick du scheduler de flag-fall.
     */
    @Query("select g.id from GameEntity g where g.status = :status and g.clockDeadlineAt is not null and g.clockDeadlineAt <= :deadline")
    List<UUID> findIdsWithExpiredClock(@Param("status") GameStatus status, @Param("deadline") Instant deadline);
}
