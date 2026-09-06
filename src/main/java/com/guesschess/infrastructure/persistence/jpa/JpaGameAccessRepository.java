package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    @Transactional
    public PlayerRef linkPlayer(GameId gameId, Color color, PlayerRef ref) {
        String type = switch (ref) {
            case PlayerRef.Account account -> "ACCOUNT";
            case PlayerRef.Anonymous anonymous -> "ANONYMOUS";
            // Le niveau est encode dans le type lui-meme ("COMPUTER_EASY"...) plutot
            // que dans une colonne dediee (etape 15) : evite une migration pour un
            // seul champ, playerId restant simplement null pour ce cas (pas de compte
            // ni d'identite anonyme a referencer).
            case PlayerRef.Computer computer -> "COMPUTER_" + computer.level().name();
        };
        UUID playerId = switch (ref) {
            case PlayerRef.Account account -> account.userId().value();
            case PlayerRef.Anonymous anonymous -> anonymous.anonymousId().value();
            case PlayerRef.Computer computer -> null;
        };
        if (color == Color.WHITE) {
            springDataRepository.linkWhitePlayerIfAbsent(gameId.value(), type, playerId);
        } else {
            springDataRepository.linkBlackPlayerIfAbsent(gameId.value(), type, playerId);
        }
        GameAccessEntity reloaded = springDataRepository.findById(gameId.value()).orElseThrow();
        return color == Color.WHITE
                ? toPlayerRef(reloaded.getWhitePlayerType(), reloaded.getWhitePlayerId())
                : toPlayerRef(reloaded.getBlackPlayerType(), reloaded.getBlackPlayerId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameAccess> findAllByAccount(UserId userId, int page, int size) {
        return springDataRepository.findAllByAccount(userId.value(), PageRequest.of(page, size)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public List<GameId> relinkAnonymousToAccount(PlayerRef.Anonymous from, PlayerRef.Account to) {
        UUID anonymousId = from.anonymousId().value();
        UUID userId = to.userId().value();
        List<GameId> affected = springDataRepository.findGameIdsByAnonymousPlayer(anonymousId).stream()
                .map(GameId::new)
                .distinct()
                .toList();
        springDataRepository.relinkWhitePlayer(anonymousId, userId);
        springDataRepository.relinkBlackPlayer(anonymousId, userId);
        return affected;
    }

    private GameAccess toDomain(GameAccessEntity entity) {
        return new GameAccess(
                new GameId(entity.getGameId()),
                new PlayerToken(entity.getWhiteToken()),
                new PlayerToken(entity.getBlackToken()),
                toPlayerRef(entity.getWhitePlayerType(), entity.getWhitePlayerId()),
                toPlayerRef(entity.getBlackPlayerType(), entity.getBlackPlayerId())
        );
    }

    private PlayerRef toPlayerRef(String type, UUID playerId) {
        if (type == null) {
            return null;
        }
        if (type.startsWith("COMPUTER_")) {
            return new PlayerRef.Computer(ComputerLevel.valueOf(type.substring("COMPUTER_".length())));
        }
        return switch (type) {
            case "ACCOUNT" -> new PlayerRef.Account(new UserId(playerId));
            case "ANONYMOUS" -> new PlayerRef.Anonymous(new AnonymousId(playerId));
            default -> throw new IllegalStateException("unknown player type: " + type);
        };
    }
}
