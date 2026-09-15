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
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MoveGenerator;
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
    void computerNeverCapturesTheOpponentsHangingKingWhenAnAlternativeMoveExists() {
        // Meme position/round que computerImmediatelyGuessesAMoveThatWouldCaptureItsOwnHangingKingInsteadOfAskingTheEngine,
        // mais roles inverses : l'ordinateur est maintenant noir, donc joueur au trait
        // (pas devineur) pour le round qui suit la devinette correcte - il a Ra8xa1
        // parmi ses coups legaux, mais ce n'est pas un coup force (le roi noir a aussi
        // des coups). Cote coup reel, ce n'est jamais "gratuit" : l'ordinateur (mover)
        // n'est pas lui-meme en echec, donc une devinette adverse correcte annule juste
        // normalement le coup plutot que de declencher une issue immediate - un humain
        // qui connait la regle la devine donc quasi systematiquement. L'ordinateur doit
        // consulter le moteur pour un autre coup plutot que de jouer cette capture
        // previsible (voir ComputerPlayerService.chooseMoveOrFallback, etape 17).
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
        chessEngine.alwaysChoose((board, legalMoves) -> harmless);

        computerPlayerService.onRoundStarted(gameId);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var submission = gameLifecycleService.viewGame(gameId, blackToken).mySubmission();
            org.junit.jupiter.api.Assertions.assertTrue(submission.submitted());
            org.junit.jupiter.api.Assertions.assertEquals(harmless, submission.move());
        });
    }

    // Le cas "capture de roi forcee (seul coup legal), donc jouee directement sans
    // consulter le moteur" est couvert au niveau MinimaxChessEngineTest plutot qu'ici :
    // toute position ou le seul coup legal restant est une capture de roi via le roi lui-
    // meme met aussi mecaniquement ce roi en echec (adjacence mutuelle), ce qui declenche
    // fast_mate (Game.applyFastMateIfApplicable) des la resolution du round precedent -
    // la partie se termine alors avant meme que ComputerPlayerService n'ait a agir pour
    // ce round, rendant ce chemin difficile a atteindre via un scenario de partie reel.

    @Test
    void computerPlaysAMoveThatTriggersFastMateEvenIfTheEngineWouldHavePickedSomethingElse() {
        // Blancs (ordinateur) au trait : Ra1-a8+ met les noirs en echec avec Kh7 pour
        // seule case de fuite (Kg8 illegal - tour a8 ; Kg7 illegal - roi blanc f6
        // adjacent) - fast_mate (voir Game.applyFastMateIfApplicable), qui exige la
        // variante GUESSCHESS. Stockfish n'evalue pas cette regle maison (voir
        // ComputerPlayerService.findFastMateMoves) : FakeChessEngine est configure
        // pour choisir un tout autre coup legal, qui ne doit jamais etre consulte des
        // lors qu'un coup fast_mate existe parmi les coups legaux de l'ordinateur.
        GameId gameId = GameId.random();
        Board position = Board.empty()
                .withPiece(Position.fromAlgebraic("f6"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));
        Game game = Game.fromPosition(gameId, position, GameVariant.GUESSCHESS);
        gameRepository.insert(game);

        PlayerToken whiteToken = PlayerToken.random();
        PlayerToken blackToken = PlayerToken.random();
        GameAccess access = new GameAccess(gameId, whiteToken, blackToken)
                .withPlayerLinked(Color.WHITE, new PlayerRef.Computer(ComputerLevel.EASY))
                .withPlayerLinked(Color.BLACK, new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID())));
        gameAccessRepository.save(access);

        // Plusieurs coups blancs declenchent fast_mate ici (Ra1-a8+ comme Ra1-h1+, la
        // tour parachevant le filet avec le roi blanc f6 des deux facons) - on
        // n'impose donc pas UN coup precis, seulement que le coup choisi verifie la
        // condition fast_mate et differe du coup inoffensif force par FakeChessEngine.
        Move harmless = findMove(game.legalMoves(), "a1", "a2");
        chessEngine.alwaysChoose((board, legalMoves) -> harmless);

        computerPlayerService.onRoundStarted(gameId);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            var submission = gameLifecycleService.viewGame(gameId, whiteToken).mySubmission();
            org.junit.jupiter.api.Assertions.assertTrue(submission.submitted());
            Move chosen = submission.move();
            org.junit.jupiter.api.Assertions.assertNotEquals(harmless, chosen);
            Board afterChosenMove = position.applyMove(chosen);
            org.junit.jupiter.api.Assertions.assertTrue(CheckDetector.isInCheck(afterChosenMove, Color.BLACK));
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    MoveGenerator.generateLegalMoves(afterChosenMove, Color.BLACK).size());
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
    void computerAvoidsRepeatingItsLastGuessOnItsNextGuessingRound() {
        // Ordinateur difficile aux noirs (devineur des le round 1, l'humain aux blancs
        // etant au trait), configure pour toujours deviner e2e4 quand ce coup est legal
        // parmi les coups a deviner (round 1 et round 3, ou les blancs sont a chaque
        // fois au trait) - verifie que guessesToAvoidThisTurn (ComputerPlayerService,
        // etape 17 strategie 3) fait figurer ce meme coup dans movesToAvoidIfPossible au
        // round 3, sa devinette suivante.
        PlayerRef human = new PlayerRef.Anonymous(new AnonymousId(UUID.randomUUID()));
        CreatedGame created = gameLifecycleService.createComputerGame(
                GameVariant.GUESSMATE, null, Color.WHITE, human, ComputerLevel.HARD);
        GameId gameId = created.gameId();
        GameAccess access = gameAccessRepository.findByGameId(gameId).orElseThrow();

        chessEngine.alwaysChoose((board, legalMoves) -> legalMoves.stream()
                .filter(m -> m.from().equals(Position.fromAlgebraic("e2")) && m.to().equals(Position.fromAlgebraic("e4")))
                .findFirst()
                .orElse(legalMoves.get(0)));

        // Round 1 : l'ordinateur (noir) devine e2e4 par avance ; les blancs jouent
        // Ng1-f3 a la place, la devinette est fausse - le coup reel est joue normalement.
        computerPlayerService.onRoundStarted(gameId);
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.blackToken()).mySubmission().submitted()));
        GameSnapshot afterRound1 = gameLifecycleService.submitMove(access.whiteToken(),
                MoveIntent.of(Position.fromAlgebraic("g1"), Position.fromAlgebraic("f3"))).orElseThrow();
        computerPlayerService.onRoundStarted(afterRound1.id());

        // Round 2 : l'ordinateur (noir) est maintenant au trait (coup reel, pas une
        // devinette - guessesToAvoidThisTurn ne s'applique pas ici). Les blancs devinent
        // volontairement a tort (pas de devinette) pour resoudre le round normalement.
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.blackToken()).mySubmission().submitted()));
        GameSnapshot afterRound2 = gameLifecycleService.submitGuess(access.whiteToken(), null).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(Color.WHITE, afterRound2.sideToMove());

        // Round 3 : les blancs sont de nouveau au trait, l'ordinateur (noir) devine de
        // nouveau par avance - e2e4 est toujours legal (le pion blanc n'a pas bouge),
        // donc de nouveau la devinette preferee par FakeChessEngine si rien ne l'evite.
        computerPlayerService.onRoundStarted(afterRound2.id());
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(
                        gameLifecycleService.viewGame(gameId, access.blackToken()).mySubmission().submitted()));
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
