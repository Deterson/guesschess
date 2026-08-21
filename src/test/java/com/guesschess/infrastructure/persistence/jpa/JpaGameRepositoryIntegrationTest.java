package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameRepository;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.move.Move;
import com.guesschess.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reprend les cas de InMemoryGameRepositoryTest contre un vrai Postgres (JpaGameRepository,
 * etape 4 de la roadmap) : withGame doit garantir le meme acces exclusif par partie que la
 * version en memoire, mais via un verrou base (SELECT ... FOR UPDATE) plutot qu'un verrou JVM.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestContainerConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=60")
class JpaGameRepositoryIntegrationTest {

    @Autowired
    private GameRepository gameRepository;

    @Test
    void findingAnUnknownGameThrows() {
        assertThrows(GameNotFoundException.class, () -> gameRepository.withGame(GameId.random(), g -> g));
    }

    @Test
    void insertingTheSameGameTwiceThrows() {
        Game game = Game.newGame();
        gameRepository.insert(game);

        assertThrows(IllegalStateException.class, () -> gameRepository.insert(game));
    }

    @Test
    void mutationsMadeInsideWithGameArePersistedAcrossCalls() {
        Game game = Game.newGame();
        gameRepository.insert(game);

        gameRepository.withGame(game.id(), g -> {
            Move e4 = g.legalMoves().stream()
                    .filter(m -> m.from().toAlgebraic().equals("e2") && m.to().toAlgebraic().equals("e4"))
                    .findFirst().orElseThrow();
            g.submitMove(e4);
            g.submitGuess(null);
            return null;
        });

        gameRepository.withGame(game.id(), g -> {
            assertEquals(1, g.moveHistory().size());
            assertEquals(GameStatus.ONGOING, g.status());
            return null;
        });
    }

    /**
     * withGame doit serialiser l'acces a une meme partie via le verrou pessimiste
     * (voir SpringDataGameJpaRepository.findByIdForUpdate), meme comportement
     * observable que la version en memoire (InMemoryGameRepositoryTest). Le pool
     * Hikari est agrandi (voir @TestPropertySource) pour que la serialisation
     * observee vienne bien du verrou DB et non d'un epuisement du pool de connexions.
     */
    @Test
    void withGameSerializesConcurrentAccessToTheSameGame() throws Exception {
        Game game = Game.newGame();
        gameRepository.insert(game);

        AtomicInteger concurrentCalls = new AtomicInteger();
        AtomicInteger maxObservedConcurrency = new AtomicInteger();
        int taskCount = 50;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                futures.add(executor.submit(() -> gameRepository.withGame(game.id(), g -> {
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
