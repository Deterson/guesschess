package com.guesschess.infrastructure.persistence.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataGameAccessJpaRepository extends JpaRepository<GameAccessEntity, UUID> {

    Optional<GameAccessEntity> findByWhiteTokenOrBlackToken(UUID whiteToken, UUID blackToken);

    @Query("select a from GameAccessEntity a where "
            + "(a.whitePlayerType = 'ACCOUNT' and a.whitePlayerId = :userId) "
            + "or (a.blackPlayerType = 'ACCOUNT' and a.blackPlayerId = :userId) "
            + "order by a.createdAt desc")
    List<GameAccessEntity> findAllByAccount(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Fusion anonyme -> compte (etape 8) : deux requetes (colonnes blanc/noir separees)
     * plutot qu'une seule avec OR, une mise a jour SQL ne pouvant pas conditionner
     * quelle paire de colonnes toucher au sein d'une meme ligne selon laquelle matche.
     */
    @Modifying
    @Query("update GameAccessEntity a set a.whitePlayerType = 'ACCOUNT', a.whitePlayerId = :userId "
            + "where a.whitePlayerType = 'ANONYMOUS' and a.whitePlayerId = :anonymousId")
    void relinkWhitePlayer(@Param("anonymousId") UUID anonymousId, @Param("userId") UUID userId);

    @Modifying
    @Query("update GameAccessEntity a set a.blackPlayerType = 'ACCOUNT', a.blackPlayerId = :userId "
            + "where a.blackPlayerType = 'ANONYMOUS' and a.blackPlayerId = :anonymousId")
    void relinkBlackPlayer(@Param("anonymousId") UUID anonymousId, @Param("userId") UUID userId);

    /**
     * Ecriture conditionnelle atomique ("premier arrive, premier lie") : une seule
     * instruction SQL, pas besoin du verrou pessimiste utilise par JpaGameRepository
     * pour l'agregat Game, puisque chaque colonne ne passe qu'une fois de null a une
     * valeur et ne change plus jamais ensuite.
     *
     * PAS de clearAutomatically ici : linkPlayer est appele depuis l'interieur du
     * withGame(...) de JpaGameRepository, dans la MEME transaction - un
     * EntityManager.clear() automatique detacherait aussi le GameEntity que
     * JpaGameRepository.withGame s'apprete encore a mettre a jour, et cette mise a
     * jour serait silencieusement perdue au commit (plus de dirty-checking sur une
     * entite detachee).
     */
    @Modifying
    @Query("update GameAccessEntity a set a.whitePlayerType = :type, a.whitePlayerId = :playerId "
            + "where a.gameId = :gameId and a.whitePlayerType is null")
    void linkWhitePlayerIfAbsent(@Param("gameId") UUID gameId, @Param("type") String type, @Param("playerId") UUID playerId);

    @Modifying
    @Query("update GameAccessEntity a set a.blackPlayerType = :type, a.blackPlayerId = :playerId "
            + "where a.gameId = :gameId and a.blackPlayerType is null")
    void linkBlackPlayerIfAbsent(@Param("gameId") UUID gameId, @Param("type") String type, @Param("playerId") UUID playerId);
}
