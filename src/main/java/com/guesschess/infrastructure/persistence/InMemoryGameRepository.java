package com.guesschess.infrastructure.persistence;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameRepository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Doublure de test pure (stockage en memoire) : remplacee en production par
 * JpaGameRepository depuis l'etape 4, plus annotee @Repository pour ne pas entrer
 * en conflit de bean avec l'implementation JPA. Toujours utilisee directement (sans
 * contexte Spring) par GameLifecycleServiceTest et testee par
 * InMemoryGameRepositoryTest comme specification comportementale de withGame.
 * compute() serialise l'acces par gameId (verrou par bucket de la
 * ConcurrentHashMap), ce qui suffit a rendre withGame atomique sans structure de
 * verrous separee.
 */
public class InMemoryGameRepository implements GameRepository {

    private final ConcurrentHashMap<GameId, Game> games = new ConcurrentHashMap<>();

    @Override
    public void insert(Game game) {
        Game existing = games.putIfAbsent(game.id(), game);
        if (existing != null) {
            throw new IllegalStateException("a game already exists for id: " + game.id());
        }
    }

    @Override
    public <T> T withGame(GameId id, Function<Game, T> action) {
        Object[] result = new Object[1];
        games.compute(id, (key, game) -> {
            if (game == null) {
                throw new GameNotFoundException(id);
            }
            result[0] = action.apply(game);
            return game;
        });
        @SuppressWarnings("unchecked")
        T typedResult = (T) result[0];
        return typedResult;
    }
}
