package com.guesschess.infrastructure.persistence;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Doublure de test pure (stockage en memoire) : remplacee en production par
 * JpaGameAccessRepository depuis l'etape 4, plus annotee @Repository pour ne pas
 * entrer en conflit de bean avec l'implementation JPA. Ecrit une seule fois par
 * partie (a la creation) puis seulement lu : une ConcurrentHashMap simple suffit,
 * pas besoin de la synchronisation par cle de InMemoryGameRepository.
 */
public class InMemoryGameAccessRepository implements GameAccessRepository {

    private final ConcurrentHashMap<PlayerToken, GameAccess> byToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<GameId, GameAccess> byGameId = new ConcurrentHashMap<>();

    @Override
    public void save(GameAccess access) {
        byToken.put(access.whiteToken(), access);
        byToken.put(access.blackToken(), access);
        byGameId.put(access.gameId(), access);
    }

    @Override
    public Optional<GameAccess> findByToken(PlayerToken token) {
        return Optional.ofNullable(byToken.get(token));
    }

    @Override
    public Optional<GameAccess> findByGameId(GameId gameId) {
        return Optional.ofNullable(byGameId.get(gameId));
    }

    @Override
    public PlayerRef linkPlayer(GameId gameId, Color color, PlayerRef ref) {
        GameAccess updated = byGameId.computeIfPresent(gameId, (id, access) -> {
            GameAccess linked = access.withPlayerLinked(color, ref);
            byToken.put(linked.whiteToken(), linked);
            byToken.put(linked.blackToken(), linked);
            return linked;
        });
        return updated == null ? null : updated.playerOf(color);
    }

    /**
     * Pas de notion de recence dans cette doublure de test (GameAccess ne porte pas de
     * createdAt, contrairement a GameAccessEntity) - ordre non garanti, sans consequence
     * pour un stockage en memoire utilise uniquement en test.
     */
    @Override
    public List<GameAccess> findAllByAccount(UserId userId, int page, int size) {
        PlayerRef account = new PlayerRef.Account(userId);
        return byGameId.values().stream()
                .filter(access -> account.equals(access.whitePlayer()) || account.equals(access.blackPlayer()))
                .sorted(Comparator.comparing(access -> access.gameId().toString()))
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    @Override
    public List<GameId> relinkAnonymousToAccount(PlayerRef.Anonymous from, PlayerRef.Account to) {
        List<GameId> affected = new ArrayList<>();
        for (GameAccess access : byGameId.values()) {
            GameAccess relinked = access;
            if (from.equals(access.whitePlayer())) {
                relinked = new GameAccess(relinked.gameId(), relinked.whiteToken(), relinked.blackToken(), to, relinked.blackPlayer());
            }
            if (from.equals(access.blackPlayer())) {
                relinked = new GameAccess(relinked.gameId(), relinked.whiteToken(), relinked.blackToken(), relinked.whitePlayer(), to);
            }
            if (relinked != access) {
                byGameId.put(relinked.gameId(), relinked);
                byToken.put(relinked.whiteToken(), relinked);
                byToken.put(relinked.blackToken(), relinked);
                affected.add(relinked.gameId());
            }
        }
        return affected;
    }
}
