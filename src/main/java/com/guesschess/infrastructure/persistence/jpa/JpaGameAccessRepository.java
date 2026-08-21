package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerToken;
import com.guesschess.domain.game.GameId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Implementation Postgres/JPA du port GameAccessRepository (etape 4 de la roadmap),
 * remplace InMemoryGameAccessRepository. Ecrit une seule fois par partie a la
 * creation, jamais modifie ensuite : pas besoin de verrou particulier.
 *
 * @Component plutot que @Repository, voir JpaGameRepository pour la raison
 * (traduction automatique d'exceptions non-JPA par Spring).
 */
@Component
class JpaGameAccessRepository implements GameAccessRepository {

    private final SpringDataGameAccessJpaRepository springDataRepository;

    JpaGameAccessRepository(SpringDataGameAccessJpaRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void save(GameAccess access) {
        springDataRepository.save(new GameAccessEntity(
                access.gameId().value(),
                access.whiteToken().value(),
                access.blackToken().value(),
                Instant.now()
        ));
    }

    @Override
    public Optional<GameAccess> findByToken(PlayerToken token) {
        return springDataRepository.findByWhiteTokenOrBlackToken(token.value(), token.value())
                .map(this::toDomain);
    }

    @Override
    public Optional<GameAccess> findByGameId(GameId gameId) {
        return springDataRepository.findById(gameId.value())
                .map(this::toDomain);
    }

    private GameAccess toDomain(GameAccessEntity entity) {
        return new GameAccess(
                new GameId(entity.getGameId()),
                new PlayerToken(entity.getWhiteToken()),
                new PlayerToken(entity.getBlackToken())
        );
    }
}
