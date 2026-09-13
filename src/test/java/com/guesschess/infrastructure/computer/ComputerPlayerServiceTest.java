package com.guesschess.infrastructure.computer;

import com.guesschess.application.CreatedGame;
import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.MoveIntent;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.application.computer.FakeChessEngine;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.infrastructure.persistence.InMemoryGameAccessRepository;
import com.guesschess.infrastructure.persistence.InMemoryGameRepository;
import com.guesschess.infrastructure.websocket.GameBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
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
    private InMemoryGameRepository gameRepository;
    private GameLifecycleService gameLifecycleService;
    private FakeChessEngine chessEngine;
    private GameBroadcastService gameBroadcastService;
    private ComputerPlayerService computerPlayerService;

    @BeforeEach
    void setUp() {
        gameAccessRepository = new InMemoryGameAccessRepository();
        gameRepository = new InMemoryGameRepository();
        chessEngine = new FakeChessEngine();
        gameLifecycleService = new GameLifecycleService(gameRepository, gameAccessRepository, chessEngine);
        gameBroadcastService = mock(GameBroadcastService.class);
        computerPlayerService = new ComputerPlayerService(gameLifecycleService, gameAccessRepository, chessEngine, gameBroadcastService);
    }

    @Test
    void computerPlaysItsOwnMoveThenGuessesTheHumanMoveOnTheNextRound() {
        PlayerRef human = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        // Humain aux noirs : l'ordinateur (blancs) est au trait des le round 1, donc
        // devra soumettre son propre coup sans attendre aucune action humaine.
        CreatedGame created = gameLifecycleService.createComputerGame(
                com.guesschess.domain.game.GameVariant.GUESSMATE, null, Color.BLACK, human, ComputerLevel.EASY);
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
                com.guesschess.domain.game.GameVariant.GUESSMATE, null, Color.BLACK, human, ComputerLevel.EASY);
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
    void computerImmediatelyGuessesAMoveThatWouldCaptureItsOwnHangingKingInsteadOfAskingTheEngine() {
        // Roi blanc en a1, en echec par la tour noire (a8), deux coups blancs legaux
        // (Ra1-b1/b2 - voir GameGuessingTest.checkWithMultipleEscapesPosition, expres
        // choisie plutot que la position a un seul coup pour ne pas declencher
        // fast_mate ici, qui terminerait la partie avant meme ce round). Devine
        // correctement puis annule : le roi blanc reste en echec non resolu, le trait
        // passe aux noirs, qui ont alors Ra8xa1 parmi leurs coups legaux (voir
        // GameGuessingTest.correctlyGuessingTheEscapeFromCheckLeavesTheKingInCheckAndPassesTheTurn).
        GameId gameId = GameId.random();
        Board position = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));
        Game game = Game.fromPosition(gameId, position, GameVariant.GUESSCHESS);
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        game.submitMove(escape);
        gameRepository.insert(game);

        PlayerToken whiteToken = PlayerToken.random();
        PlayerToken blackToken = PlayerToken.random();
        GameAccess access = new GameAccess(gameId, whiteToken, blackToken)
                .withPlayerLinked(Color.WHITE, new PlayerRef.Computer(ComputerLevel.EASY))
                .withPlayerLinked(Color.BLACK, new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID())));
        gameAccessRepository.save(access);

        // Le moteur choisirait un tout autre coup s'il etait consulte - il ne doit
        // meme pas etre appele des lors qu'une capture du roi blanc est disponible.
        Move harmless = findMove(game.legalMoves(), "h8", "h7");
        Move captureKing = findMove(game.legalMoves(), "a8", "a1");
        chessEngine.alwaysChoose((board, legalMoves) -> harmless);

        computerPlayerService.onRoundStarted(gameId);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var submission = gameLifecycleService.viewGame(gameId, whiteToken).mySubmission();
            org.junit.jupiter.api.Assertions.assertTrue(submission.submitted());
            org.junit.jupiter.api.Assertions.assertEquals(captureKing, submission.move());
        });
    }

    @Test
    void computerImmediatelyPlaysAMoveThatCapturesTheOpponentsHangingKingInsteadOfAskingTheEngine() {
        // Meme position/round que computerImmediatelyGuessesAMoveThatWouldCaptureItsOwnHangingKingInsteadOfAskingTheEngine,
        // mais roles inverses : l'ordinateur est maintenant noir, donc joueur au
        // trait (pas devineur) pour le round qui suit la devinette correcte - il a
        // Ra8xa1 parmi ses coups legaux et doit le jouer directement, sans jamais
        // interroger Stockfish (voir ComputerPlayerService.chooseMoveOrFallback).
        GameId gameId = GameId.random();
        Board position = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));
        Game game = Game.fromPosition(gameId, position, GameVariant.GUESSCHESS);
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        game.submitMove(escape);
        gameRepository.insert(game);

        PlayerToken whiteToken = PlayerToken.random();
        PlayerToken blackToken = PlayerToken.random();
        GameAccess access = new GameAccess(gameId, whiteToken, blackToken)
                .withPlayerLinked(Color.WHITE, new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID())))
                .withPlayerLinked(Color.BLACK, new PlayerRef.Computer(ComputerLevel.HARD));
        gameAccessRepository.save(access);

        Move harmless = findMove(game.legalMoves(), "h8", "h7");
        Move captureKing = findMove(game.legalMoves(), "a8", "a1");
        chessEngine.alwaysChoose((board, legalMoves) -> harmless);

        computerPlayerService.onRoundStarted(gameId);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var submission = gameLifecycleService.viewGame(gameId, blackToken).mySubmission();
            org.junit.jupiter.api.Assertions.assertTrue(submission.submitted());
            org.junit.jupiter.api.Assertions.assertEquals(captureKing, submission.move());
        });
    }

    @Test
    void computerAvoidsReplayingAMoveThatWasJustGuessedCorrectlyForItsNextRealMove() {
        // Ordinateur difficile aux blancs, humain aux noirs, position de depart : les
        // noirs devinent e2e4 par avance (round 1), forcement correct puisque
        // l'ordinateur (FakeChessEngine) est configure pour toujours prefere ce coup
        // s'il est legal - verifie que BlockedMove (ComputerPlayerService) le fait
        // ensuite figurer dans movesToAvoidIfPossible au prochain coup reel de
        // l'ordinateur (round 3, apres le round 2 ou les noirs jouent), voir
        // ComputerPlayerService.movesToAvoidThisTurn.
        PlayerRef human = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        CreatedGame created = gameLifecycleService.createComputerGame(
                GameVariant.GUESSMATE, null, Color.BLACK, human, ComputerLevel.HARD);
        GameId gameId = created.gameId();
        GameAccess access = gameAccessRepository.findByGameId(gameId).orElseThrow();

        chessEngine.alwaysChoose((board, legalMoves) -> legalMoves.stream()
                .filter(m -> m.from().equals(Position.fromAlgebraic("e2")) && m.to().equals(Position.fromAlgebraic("e4")))
                .findFirst()
                .orElse(legalMoves.get(0)));

        // Round 1 : les noirs devinent e2e4 par avance.
        gameLifecycleService.submitGuess(access.blackToken(), MoveIntent.of(
                Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4")));
        computerPlayerService.onRoundStarted(gameId);

        // Round 1 resolu (devinette correcte, coup annule) des que l'ordinateur a
        // soumis son coup puis, en cascade, sa propre devinette pour le round 2 (il
        // devient devineur - voir onRoundStarted/act) : attendre cette devinette plutot
        // que juste la resolution du round 1, sinon la soumission des noirs ci-dessous
        // court-circuiterait la devinette de l'ordinateur.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.whiteToken()).mySubmission().submitted()));
        org.junit.jupiter.api.Assertions.assertEquals(Color.BLACK, gameLifecycleService.viewGame(gameId).sideToMove());

        // Round 2 : les noirs (humain) jouent un coup reel quelconque, resolvant le
        // round puisque l'ordinateur a deja devine par avance ci-dessus.
        Move blackMove = gameLifecycleService.viewGame(gameId).legalMoves().get(0);
        GameSnapshot afterRound2 = gameLifecycleService.submitMove(access.blackToken(),
                MoveIntent.of(blackMove.from(), blackMove.to())).orElseThrow();
        computerPlayerService.onRoundStarted(afterRound2.id());

        // Round 3 : de nouveau au trait, l'ordinateur doit avoir recu e2e4 dans
        // movesToAvoidIfPossible (encore legal : son round 1 a ete annule, le pion
        // blanc n'a jamais bouge).
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.whiteToken()).mySubmission().submitted()));
        Move e2e4 = findMove(gameLifecycleService.viewGame(gameId).legalMoves(), "e2", "e4");
        org.junit.jupiter.api.Assertions.assertTrue(chessEngine.lastMovesToAvoidIfPossible().contains(e2e4));
    }

    @Test
    void onRoundStartedDoesNothingForAHumanVsHumanGame() {
        PlayerRef white = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        PlayerRef black = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        CreatedGame created = gameLifecycleService.createGame(com.guesschess.domain.game.GameVariant.GUESSMATE, null, Color.WHITE, white);
        gameAccessRepository.linkPlayer(created.gameId(), Color.BLACK, black);

        computerPlayerService.onRoundStarted(created.gameId());

        verify(gameBroadcastService, times(0)).broadcast(argThat(snapshot -> snapshot.id().equals(created.gameId())));
    }

    private static Move findMove(List<Move> moves, String from, String to) {
        Position fromPos = Position.fromAlgebraic(from);
        Position toPos = Position.fromAlgebraic(to);
        return moves.stream()
                .filter(m -> m.from().equals(fromPos) && m.to().equals(toPos))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no legal move " + from + "-" + to));
    }
}
