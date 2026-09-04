package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.piece.Color;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                freshState.drawOfferedBy(),
                freshState.rematchOfferedBy(),
                freshState.rematchGameId(),
                null, null, 0L, 0L, null, null);
        GameEntity legacyEntity = new GameEntity(
                UUID.randomUUID(), GameVariant.GUESSCHESS, GameStatus.ONGOING,
                null, null, Color.WHITE, legacyState, null, Instant.now(), Instant.now());

        Game game = mapper.toDomain(legacyEntity);

        assertTrue(game.roundHistoryWithPositions().isEmpty());
    }

    /**
     * Regression (etape 12) : une partie persistee avant l'ajout de la pendule n'a
     * AUCUNE des cles de cadence dans son JSON (pas juste des valeurs null comme dans
     * le test ci-dessus, qui construit le record directement en Java sans jamais
     * passer par Jackson) - deserialiser whiteMillisRemaining/blackMillisRemaining
     * comme des `long` primitifs faisait planter (MismatchedInputException :
     * "Cannot map `null` into type `long`") des qu'un vrai joueur rouvrait une partie
     * anonyme terminee avant cette etape. Corrige en les rendant `Long` (nullable,
     * meme traitement que timeControlBaseMillis) - ce test passe par le vrai
     * GameStateJsonConverter plutot que de construire GameStateJson en Java, pour ne
     * pas manquer une regression de ce type une seconde fois.
     */
    @Test
    void toDomainDefaultsClockFieldsForAGameJsonPersistedBeforeTimers() {
        GameStateJsonConverter converter = new GameStateJsonConverter();
        ObjectMapper rawMapper = new ObjectMapper();
        String freshJson = converter.convertToDatabaseColumn(mapper.toNewEntity(Game.newGame()).getState());
        ObjectNode legacyNode = (ObjectNode) rawMapper.readTree(freshJson);
        legacyNode.remove(List.of(
                "timeControlBaseMillis", "timeControlIncrementMillis",
                "whiteMillisRemaining", "blackMillisRemaining",
                "clockRunningFor", "clockRunningSinceEpochMillis"));
        GameStateJson legacyState = converter.convertToEntityAttribute(rawMapper.writeValueAsString(legacyNode));
        GameEntity legacyEntity = new GameEntity(
                UUID.randomUUID(), GameVariant.GUESSCHESS, GameStatus.ONGOING,
                null, null, Color.WHITE, legacyState, null, Instant.now(), Instant.now());

        Game game = mapper.toDomain(legacyEntity);

        assertNull(game.timeControl());
        assertEquals(0L, game.millisRemaining(Color.WHITE));
        assertEquals(0L, game.millisRemaining(Color.BLACK));
        assertNull(game.clockRunningFor());
    }
}
