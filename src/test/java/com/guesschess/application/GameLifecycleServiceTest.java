package com.guesschess.application;

import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.persistence.InMemoryGameAccessRepository;
import com.guesschess.infrastructure.persistence.InMemoryGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitaire pur (pas de contexte Spring) : instancie le service directement avec
 * les adaptateurs en memoire, comme le fait la configuration Spring en production.
 */
class GameLifecycleServiceTest {

    private GameLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new GameLifecycleService(new InMemoryGameRepository(), new InMemoryGameAccessRepository());
    }

    @Test
    void createGameReturnsTwoDistinctTokensForANewGame() {
        CreatedGame first = service.createGame();
        CreatedGame second = service.createGame();

        assertNotEquals(first.whiteToken(), first.blackToken());
        assertNotEquals(first.gameId(), second.gameId());
    }

    @Test
    void moveWaitsForTheGuessBeforeResolving() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));

        Optional<GameSnapshot> immediate = service.submitMove(game.whiteToken(), e4);
        assertTrue(immediate.isEmpty());

        GameSnapshot snapshot = service.submitGuess(game.blackToken(), null).orElseThrow();

        assertEquals(Color.BLACK, snapshot.sideToMove());
        assertTrue(snapshot.lastRoundResult().movePlayed());
        assertEquals(GameStatus.ONGOING, snapshot.status());
    }

    @Test
    void submitMoveByTheBlackTokenIsRejectedWhenWhiteIsToMove() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));

        assertThrows(WrongTurnException.class, () -> service.submitMove(game.blackToken(), e4));
    }

    @Test
    void submitGuessByTheMoverTokenIsRejected() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));

        assertThrows(WrongTurnException.class, () -> service.submitGuess(game.whiteToken(), e4));
    }

    @Test
    void submitMoveWithAnUnknownTokenIsRejected() {
        service.createGame();
        PlayerToken unrelatedToken = PlayerToken.random();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));

        assertThrows(UnknownPlayerTokenException.class, () -> service.submitMove(unrelatedToken, e4));
    }

    @Test
    void submitMoveWithAGeometricallyImpossibleIntentIsRejected() {
        CreatedGame game = service.createGame();
        MoveIntent impossible = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e5"));

        assertThrows(NoSuchLegalMoveException.class, () -> service.submitMove(game.whiteToken(), impossible));
    }

    @Test
    void correctGuessCancelsTheMoveAndPassesTurnWithoutPlayingIt() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));

        service.submitGuess(game.blackToken(), e4);
        GameSnapshot snapshot = service.submitMove(game.whiteToken(), e4).orElseThrow();

        assertFalse(snapshot.lastRoundResult().movePlayed());
        assertTrue(snapshot.lastRoundResult().guessedCorrectly());
        assertEquals(Color.BLACK, snapshot.sideToMove());
    }

    @Test
    void viewGameReflectsTheSameGameForBothTokens() {
        CreatedGame game = service.createGame();

        GameSnapshot snapshot = service.viewGame(game.gameId());

        assertEquals(game.gameId(), snapshot.id());
        assertEquals(GameStatus.ONGOING, snapshot.status());
    }
}
