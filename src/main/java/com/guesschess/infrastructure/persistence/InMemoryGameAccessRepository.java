package com.guesschess.infrastructure.persistence;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerToken;
import com.guesschess.domain.game.GameId;

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
}
