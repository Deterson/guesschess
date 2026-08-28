package com.guesschess.application;

import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
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
    void viewGameWithTokenExposesMySubmissionOnlyToTheSubmittingColor() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));

        assertFalse(service.viewGame(game.gameId(), game.whiteToken()).mySubmission().submitted());

        service.submitMove(game.whiteToken(), e4);

        GameView moverView = service.viewGame(game.gameId(), game.whiteToken());
        assertTrue(moverView.mySubmission().submitted());
        assertEquals(e4.from(), moverView.mySubmission().move().from());
        assertEquals(e4.to(), moverView.mySubmission().move().to());

        assertFalse(service.viewGame(game.gameId(), game.blackToken()).mySubmission().submitted());
    }

    @Test
    void viewGameWithoutOrUnrelatedTokenNeverExposesASubmission() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));
        service.submitMove(game.whiteToken(), e4);

        assertFalse(service.viewGame(game.gameId(), null).mySubmission().submitted());
        assertFalse(service.viewGame(game.gameId(), PlayerToken.random()).mySubmission().submitted());
    }

    @Test
    void viewGameWithTokenExposesAnExplicitNoGuessAsSubmitted() {
        CreatedGame game = service.createGame();

        service.submitGuess(game.blackToken(), null);

        GameView guesserView = service.viewGame(game.gameId(), game.blackToken());
        assertTrue(guesserView.mySubmission().submitted());
        assertNull(guesserView.mySubmission().move());
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
    void aColorAlreadyLinkedRejectsActionsFromADifferentRequester() {
        CreatedGame game = service.createGame();
        MoveIntent e4 = MoveIntent.of(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"));
        PlayerRef firstWhiteRequester = new PlayerRef.Anonymous(AnonymousId.random());
        PlayerRef blackRequester = new PlayerRef.Account(UserId.random());

        service.submitMove(game.whiteToken(), e4, firstWhiteRequester);
        // guess null (incorrecte) : le coup est joue, le trait passe a Black.
        service.submitGuess(game.blackToken(), null, blackRequester);

        // Round 2 : White est maintenant le devineur (Black au trait) - meme couleur,
        // meme jeton, mais une identite differente : doit etre rejete (etape 7,
        // durcissement) plutot que silencieusement accepte sans ecraser le lien.
        PlayerRef secondWhiteRequester = new PlayerRef.Account(UserId.random());
        assertThrows(NotYourColorException.class,
                () -> service.submitGuess(game.whiteToken(), null, secondWhiteRequester));

        GameAccess access = gameAccessRepository.findByGameId(game.gameId()).orElseThrow();
        assertEquals(firstWhiteRequester, access.playerOf(Color.WHITE));
    }

    @Test
    void creatingAGameWithAChosenColorLinksOnlyThatColor() {
        PlayerRef creator = new PlayerRef.Account(UserId.random());

        CreatedGame game = service.createGame(GameVariant.REGULAR, Color.BLACK, creator);

        GameAccess access = gameAccessRepository.findByGameId(game.gameId()).orElseThrow();
        assertEquals(creator, access.playerOf(Color.BLACK));
        assertNull(access.playerOf(Color.WHITE));
    }

    @Test
    void joiningClaimsTheOnlyOpenColorAndReportsSuccess() {
        CreatedGame game = service.createGame(GameVariant.REGULAR, Color.WHITE, new PlayerRef.Anonymous(AnonymousId.random()));
        PlayerRef opponent = new PlayerRef.Anonymous(AnonymousId.random());

        JoinResult result = service.joinGame(game.gameId(), opponent);

        assertEquals(game.gameId(), result.gameId());
        assertEquals(Color.BLACK, result.color());
        assertEquals(game.blackToken(), result.token());
        assertTrue(result.linkedToRequester());
        GameAccess access = gameAccessRepository.findByGameId(game.gameId()).orElseThrow();
        assertEquals(opponent, access.playerOf(Color.BLACK));
    }

    @Test
    void joiningAGameThatIsAlreadyFullIsRejected() {
        CreatedGame game = service.createGame(GameVariant.REGULAR, Color.WHITE, new PlayerRef.Anonymous(AnonymousId.random()));
        service.joinGame(game.gameId(), new PlayerRef.Anonymous(AnonymousId.random()));
        PlayerRef thirdVisitor = new PlayerRef.Account(UserId.random());

        assertThrows(NoOpenColorException.class, () -> service.joinGame(game.gameId(), thirdVisitor));
    }

    @Test
    void joiningAnUnknownGameIsRejected() {
        PlayerRef requester = new PlayerRef.Anonymous(AnonymousId.random());

        assertThrows(GameNotFoundException.class, () -> service.joinGame(GameId.random(), requester));
    }

    @Test
    void findMyAccessRecoversTheTokenAndColorFromIdentityAlone() {
        CreatedGame game = service.createGame(GameVariant.REGULAR, Color.WHITE, new PlayerRef.Anonymous(AnonymousId.random()));
        PlayerRef requester = new PlayerRef.Anonymous(AnonymousId.random());
        service.joinGame(game.gameId(), requester);

        MyAccess found = service.findMyAccess(game.gameId(), requester).orElseThrow();

        assertEquals(Color.BLACK, found.color());
        assertEquals(game.blackToken(), found.token());
    }

    @Test
    void findMyAccessIsEmptyForAnIdentityNotLinkedToEitherColor() {
        CreatedGame game = service.createGame(GameVariant.REGULAR, Color.WHITE, new PlayerRef.Anonymous(AnonymousId.random()));
        PlayerRef stranger = new PlayerRef.Account(UserId.random());

        assertTrue(service.findMyAccess(game.gameId(), stranger).isEmpty());
    }

    @Test
    void findMyAccessWithAnUnresolvedIdentityIsEmpty() {
        CreatedGame game = service.createGame(GameVariant.REGULAR, Color.WHITE, new PlayerRef.Anonymous(AnonymousId.random()));

        assertTrue(service.findMyAccess(game.gameId(), null).isEmpty());
    }

    @Test
    void findMyAccessForAnUnknownGameIsRejected() {
        PlayerRef requester = new PlayerRef.Anonymous(AnonymousId.random());

        assertThrows(GameNotFoundException.class, () -> service.findMyAccess(GameId.random(), requester));
    }
}
