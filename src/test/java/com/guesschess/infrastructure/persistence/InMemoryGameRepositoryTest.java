package com.guesschess.infrastructure.persistence;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryGameRepositoryTest {

    @Test
    void findingAnUnknownGameThrows() {
        InMemoryGameRepository repository = new InMemoryGameRepository();

        assertThrows(GameNotFoundException.class, () -> repository.withGame(GameId.random(), g -> g));
    }

    @Test
    void insertingTheSameGameTwiceThrows() {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Game game = Game.newGame();
        repository.insert(game);

        assertThrows(IllegalStateException.class, () -> repository.insert(game));
    }

    /**
     * withGame doit serialiser l'acces a une meme partie : necessaire puisque Game
     * est mutable et que plusieurs connexions WebSocket peuvent la viser en meme
     * temps. On le verifie en mesurant qu'aucune paire d'appels concurrents ne
     * s'execute jamais en meme temps pour la meme cle.
     */
    @Test
    void withGameSerializesConcurrentAccessToTheSameGame() throws Exception {
        InMemoryGameRepository repository = new InMemoryGameRepository();
        Game game = Game.newGame();
        repository.insert(game);

        AtomicInteger concurrentCalls = new AtomicInteger();
        AtomicInteger maxObservedConcurrency = new AtomicInteger();
        int taskCount = 50;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> repository.withGame(game.id(), g -> {
                    int current = concurrentCalls.incrementAndGet();
                    maxObservedConcurrency.updateAndGet(max -> Math.max(max, current));
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    concurrentCalls.decrementAndGet();
                    return null;
                })));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertEquals(1, maxObservedConcurrency.get());
    }
}
