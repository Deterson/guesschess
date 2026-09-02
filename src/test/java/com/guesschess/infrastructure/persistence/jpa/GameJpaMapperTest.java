package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.piece.Color;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GameJpaMapperTest {

    private final GameJpaMapper mapper = new GameJpaMapper();

    /**
     * roundHistory/positionHistory (etape 10) sont absents du JSON persiste par un
     * code plus ancien - null a la deserialisation plutot qu'une liste vide. Doit
     * degrader en "aucun round/position connu" plutot que planter (voir "Mes parties",
     * etape 8, qui parcourt tout l'historique d'un compte et a ete la premiere a
     * retomber sur ce cas en pratique).
     */
    @Test
    void toDomainTreatsALegacyNullRoundHistoryAndPositionHistoryAsEmpty() {
        GameStateJson freshState = mapper.toNewEntity(Game.newGame()).getState();
        GameStateJson legacyState = new GameStateJson(
                freshState.board(),
                null,
                freshState.pendingMove(),
                freshState.guessSubmitted(),
                freshState.pendingGuess(),
                null,
                freshState.whiteGuessedMove(),
                freshState.whiteGuessedMoveStreak(),
                freshState.blackGuessedMove(),
                freshState.blackGuessedMoveStreak(),
                freshState.drawOfferedBy());
        GameEntity legacyEntity = new GameEntity(
                UUID.randomUUID(), GameVariant.GUESSCHESS, GameStatus.ONGOING,
                null, null, Color.WHITE, legacyState, Instant.now(), Instant.now());

        Game game = mapper.toDomain(legacyEntity);

        assertTrue(game.roundHistoryWithPositions().isEmpty());
    }
}
