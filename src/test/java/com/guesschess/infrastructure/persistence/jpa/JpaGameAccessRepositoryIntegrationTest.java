package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerToken;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameRepository;
import com.guesschess.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * game_access.game_id porte une contrainte FK vers games(id) (V2 migration) : chaque
 * test insere d'abord une vraie partie via GameRepository avant de sauver son acces.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestContainerConfig.class)
@ActiveProfiles("test")
class JpaGameAccessRepositoryIntegrationTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameAccessRepository gameAccessRepository;

    @Test
    void savedAccessCanBeFoundByEitherTokenOrByGameId() {
        Game game = Game.newGame();
        gameRepository.insert(game);
        GameId gameId = game.id();
        PlayerToken whiteToken = PlayerToken.random();
        PlayerToken blackToken = PlayerToken.random();
        gameAccessRepository.save(new GameAccess(gameId, whiteToken, blackToken));

        GameAccess byWhiteToken = gameAccessRepository.findByToken(whiteToken).orElseThrow();
        GameAccess byBlackToken = gameAccessRepository.findByToken(blackToken).orElseThrow();
        GameAccess byGameId = gameAccessRepository.findByGameId(gameId).orElseThrow();

        assertEquals(gameId, byWhiteToken.gameId());
        assertEquals(gameId, byBlackToken.gameId());
        assertEquals(gameId, byGameId.gameId());
    }

    @Test
    void findingAnUnknownTokenOrGameIdReturnsEmpty() {
        assertTrue(gameAccessRepository.findByToken(PlayerToken.random()).isEmpty());
        assertTrue(gameAccessRepository.findByGameId(GameId.random()).isEmpty());
    }
}
