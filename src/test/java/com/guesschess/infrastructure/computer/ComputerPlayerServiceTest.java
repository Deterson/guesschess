package com.guesschess.infrastructure.computer;

import com.guesschess.application.CreatedGame;
import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.application.computer.FakeChessEngine;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.persistence.InMemoryGameAccessRepository;
import com.guesschess.infrastructure.persistence.InMemoryGameRepository;
import com.guesschess.infrastructure.websocket.GameBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifie le declenchement automatique de l'ordinateur (etape 15) : quel que soit son
 * role pour le round en cours (joueur au trait ou devineur, voir
 * ComputerPlayerService.onRoundStarted), sans jamais passer par un vrai process
 * Stockfish (FakeChessEngine). Le calcul tournant sur un thread virtuel separe (voir
 * ComputerPlayerService), les assertions attendent (Awaitility) plutot que de
 * supposer une completion synchrone.
 */
class ComputerPlayerServiceTest {

    private GameAccessRepository gameAccessRepository;
    private GameLifecycleService gameLifecycleService;
    private FakeChessEngine chessEngine;
    private GameBroadcastService gameBroadcastService;
    private ComputerPlayerService computerPlayerService;

    @BeforeEach
    void setUp() {
        gameAccessRepository = new InMemoryGameAccessRepository();
        chessEngine = new FakeChessEngine();
        gameLifecycleService = new GameLifecycleService(new InMemoryGameRepository(), gameAccessRepository, chessEngine);
        gameBroadcastService = mock(GameBroadcastService.class);
        computerPlayerService = new ComputerPlayerService(gameLifecycleService, gameAccessRepository, chessEngine, gameBroadcastService);
    }

    @Test
    void computerPlaysItsOwnMoveThenGuessesTheHumanMoveOnTheNextRound() {
        PlayerRef human = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        // Humain aux noirs : l'ordinateur (blancs) est au trait des le round 1, donc
        // devra soumettre son propre coup sans attendre aucune action humaine.
        CreatedGame created = gameLifecycleService.createComputerGame(
                com.guesschess.domain.game.GameVariant.GUESSCHESS, null, Color.BLACK, human, ComputerLevel.EASY);
        GameId gameId = created.gameId();
        GameAccess access = gameAccessRepository.findByGameId(gameId).orElseThrow();

        computerPlayerService.onRoundStarted(gameId);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.whiteToken()).mySubmission().submitted()));
        // Round pas encore resolu : l'humain (noir) n'a encore rien soumis.
        verify(gameBroadcastService, times(0)).broadcast(any());

        // L'humain devine (a tort, "pas de devinette") : complete la paire, le round
        // se resout, le trait passe aux noirs - c'est alors a l'ordinateur (blancs) de
        // deviner pour le round suivant.
        GameSnapshot resolved = gameLifecycleService.submitGuess(access.blackToken(), null).orElseThrow();
        computerPlayerService.onRoundStarted(resolved.id());

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.whiteToken()).mySubmission().submitted()));
        org.junit.jupiter.api.Assertions.assertEquals(Color.BLACK, gameLifecycleService.viewGame(gameId).sideToMove());
    }

    @Test
    void computerFallsBackToALegalMoveWhenTheEngineFailsInsteadOfLeavingTheRoundStuck() {
        PlayerRef human = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        CreatedGame created = gameLifecycleService.createComputerGame(
                com.guesschess.domain.game.GameVariant.GUESSCHESS, null, Color.BLACK, human, ComputerLevel.EASY);
        GameId gameId = created.gameId();
        GameAccess access = gameAccessRepository.findByGameId(gameId).orElseThrow();

        chessEngine.alwaysChoose((board, legalMoves) -> {
            throw new RuntimeException("simulated engine failure (e.g. Stockfish process crash)");
        });

        computerPlayerService.onRoundStarted(gameId);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.whiteToken()).mySubmission().submitted()));
    }

    @Test
    void onRoundStartedDoesNothingForAHumanVsHumanGame() {
        PlayerRef white = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        PlayerRef black = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        CreatedGame created = gameLifecycleService.createGame(com.guesschess.domain.game.GameVariant.GUESSCHESS, null, Color.WHITE, white);
        gameAccessRepository.linkPlayer(created.gameId(), Color.BLACK, black);

        computerPlayerService.onRoundStarted(created.gameId());

        verify(gameBroadcastService, times(0)).broadcast(argThat(snapshot -> snapshot.id().equals(created.gameId())));
    }
}
