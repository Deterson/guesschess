package com.guesschess.application;

import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.UserId;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test unitaire pur (pas de contexte Spring) : instancie le service directement avec
 * les adaptateurs en memoire, comme le fait la configuration Spring en production.
 */
class GameLifecycleServiceTest {

    private GameLifecycleService service;
    private GameAccessRepository gameAccessRepository;

    @BeforeEach
    void setUp() {
        gameAccessRepository = new InMemoryGameAccessRepository();
        service = new GameLifecycleService(new InMemoryGameRepository(), gameAccessRepository);
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

    @Test
    void submittingAMoveWithARequesterLinksItToTheTokenColor() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));
        PlayerRef requester = new PlayerRef.Account(UserId.random());

        service.submitMove(game.whiteToken(), e4, requester);

        GameAccess access = gameAccessRepository.findByGameId(game.gameId()).orElseThrow();
        assertEquals(requester, access.playerOf(Color.WHITE));
        assertNull(access.playerOf(Color.BLACK));
    }

    @Test
    void submittingAGuessWithARequesterLinksItToTheTokenColor() {
        CreatedGame game = service.createGame();
        PlayerRef requester = new PlayerRef.Anonymous(AnonymousId.random());

        service.submitGuess(game.blackToken(), null, requester);

        GameAccess access = gameAccessRepository.findByGameId(game.gameId()).orElseThrow();
        assertEquals(requester, access.playerOf(Color.BLACK));
    }

    @Test
    void aColorAlreadyLinkedIsNeverRelinkedToADifferentRequester() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));
        PlayerRef firstWhiteRequester = new PlayerRef.Anonymous(AnonymousId.random());
        PlayerRef blackRequester = new PlayerRef.Account(UserId.random());

        service.submitMove(game.whiteToken(), e4, firstWhiteRequester);
        // guess null (incorrecte) : le coup est joue, le trait passe a Black.
        service.submitGuess(game.blackToken(), null, blackRequester);

        // Round 2 : White est maintenant le devineur (Black au trait) - meme couleur,
        // meme jeton, mais une identite differente : ne doit pas ecraser le lien pose
        // au round 1.
        PlayerRef secondWhiteRequester = new PlayerRef.Account(UserId.random());
        service.submitGuess(game.whiteToken(), null, secondWhiteRequester);

        GameAccess access = gameAccessRepository.findByGameId(game.gameId()).orElseThrow();
        assertEquals(firstWhiteRequester, access.playerOf(Color.WHITE));
    }
}
