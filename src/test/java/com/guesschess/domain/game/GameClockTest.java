package com.guesschess.domain.game;

import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pendule (etape 12) : le tout premier round de la partie est gratuit (ni decompte ni
 * increment, pour le coup comme pour la devinette) - le decompte reel ne demarre qu'au
 * round suivant. Au-dela de ce premier round : une seule pendule active a la fois,
 * increment toujours credite sur le coup reel (meme annule), pendule de devinette non
 * consommee par une devinette anticipee, resoumission neutre pour la pendule, et
 * flag-fall.
 */
class GameClockTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void correspondenceGameHasNoClockAtAll() {
        Game game = Game.newGame();

        assertNull(game.timeControl());
        assertNull(game.clockRunningFor());
        assertFalse(game.forfeitOnTimeIfExpired(T0.plusSeconds(999_999)));
    }

    @Test
    void firstMoveAndFirstGuessDoNotConsumeTimeOrCreditIncrement() {
        Game game = timedGame(5, 3);
        Move e4 = findMove(game.legalMoves(), "e2", "e4");

        game.submitMove(e4, T0.plusSeconds(120));
        // Le devineur non plus ne doit pas voir sa pendule demarrer pour ce premier round.
        assertNull(game.clockRunningFor());
        assertEquals(5 * 60_000L, game.millisRemaining(Color.WHITE));

        game.submitGuess(null, T0.plusSeconds(200));

        assertEquals(5 * 60_000L, game.millisRemaining(Color.WHITE));
        assertEquals(5 * 60_000L, game.millisRemaining(Color.BLACK));
    }

    @Test
    void secondRoundActuallyStartsTheClockForItsMover() {
        Game game = timedGame(5, 0);
        Instant firstRoundGuessAt = T0.plusSeconds(300);
        game.submitMove(findMove(game.legalMoves(), "e2", "e4"), T0.plusSeconds(100));

        game.submitGuess(null, firstRoundGuessAt);

        assertEquals(Color.BLACK, game.clockRunningFor());
        assertEquals(firstRoundGuessAt, game.clockRunningSince());
        assertEquals(5 * 60_000L, game.millisRemaining(Color.BLACK));
    }

    @Test
    void submitMoveStopsTheMoversClockCreditsIncrementAndStartsTheGuessersClock() {
        Game game = timedGame(5, 3);
        playFreeFirstRound(game);
        Instant afterTenSeconds = T0.plusSeconds(10);

        game.submitMove(findMove(game.legalMoves(), "e7", "e5"), afterTenSeconds);

        long expectedBlackRemaining = 5 * 60_000L - 10_000L + 3_000L;
        assertEquals(expectedBlackRemaining, game.millisRemaining(Color.BLACK));
        assertEquals(Color.WHITE, game.clockRunningFor());
        assertEquals(afterTenSeconds, game.clockRunningSince());
    }

    @Test
    void resubmittingTheMoveDoesNotChargeAdditionalTimeOrIncrement() {
        Game game = timedGame(5, 3);
        playFreeFirstRound(game);
        Move e5 = findMove(game.legalMoves(), "e7", "e5");
        Move d5 = findMove(game.legalMoves(), "d7", "d5");
        game.submitMove(e5, T0.plusSeconds(10));
        long remainingAfterFirstSubmission = game.millisRemaining(Color.BLACK);
        Instant clockRunningSinceAfterFirstSubmission = game.clockRunningSince();

        game.submitMove(d5, T0.plusSeconds(25));

        assertEquals(remainingAfterFirstSubmission, game.millisRemaining(Color.BLACK));
        assertEquals(Color.WHITE, game.clockRunningFor());
        assertEquals(clockRunningSinceAfterFirstSubmission, game.clockRunningSince());
    }

    @Test
    void aGuessSubmittedBeforeTheRealMoveNeverStartsTheGuessersClock() {
        Game game = timedGame(5, 0);
        playFreeFirstRound(game);
        long whiteRemainingBeforeAnything = game.millisRemaining(Color.WHITE);

        game.submitGuess(null, T0.plusSeconds(20));
        game.submitMove(findMove(game.legalMoves(), "e7", "e5"), T0.plusSeconds(45));

        assertEquals(whiteRemainingBeforeAnything, game.millisRemaining(Color.WHITE));
    }

    @Test
    void submitGuessStopsTheGuessersClockWithoutIncrement() {
        Game game = timedGame(5, 3);
        playFreeFirstRound(game);
        game.submitMove(findMove(game.legalMoves(), "e7", "e5"), T0.plusSeconds(10));
        Instant resolutionInstant = T0.plusSeconds(25);

        game.submitGuess(null, resolutionInstant);

        // 15s consommees en devinant (pas d'increment, deviner n'est pas jouer un
        // coup), puis la pendule de WHITE redemarre aussitot pour son propre tour de
        // mover (le trait passe toujours au devineur - voir resolveRound).
        long expectedWhiteRemaining = 5 * 60_000L - 15_000L;
        assertEquals(expectedWhiteRemaining, game.millisRemaining(Color.WHITE));
        assertEquals(Color.WHITE, game.clockRunningFor());
        assertEquals(resolutionInstant, game.clockRunningSince());
    }

    @Test
    void nextRoundStartsTheClockOfWhoeverJustGuessedRegardlessOfOutcome() {
        Game game = timedGame(5, 0);
        playFreeFirstRound(game);
        game.submitMove(findMove(game.legalMoves(), "e7", "e5"), T0.plusSeconds(5));
        Instant resolutionInstant = T0.plusSeconds(8);

        game.submitGuess(null, resolutionInstant);

        assertEquals(Color.WHITE, game.clockRunningFor());
        assertEquals(resolutionInstant, game.clockRunningSince());
    }

    @Test
    void incrementIsCreditedToTheMoverEvenWhenTheRealMoveIsCancelledByACorrectGuess() {
        Game game = timedGame(5, 3);
        playFreeFirstRound(game);
        Move e5 = findMove(game.legalMoves(), "e7", "e5");

        game.submitMove(e5, T0.plusSeconds(10));
        game.submitGuess(e5, T0.plusSeconds(10));

        long expectedBlackRemaining = 5 * 60_000L - 10_000L + 3_000L;
        assertEquals(expectedBlackRemaining, game.millisRemaining(Color.BLACK));
        assertTrue(game.roundHistory().get(1).guessedCorrectly());
        assertFalse(game.roundHistory().get(1).movePlayed());
    }

    @Test
    void forfeitOnTimeIfExpiredDoesNothingBeforeTheDeadline() {
        Game game = timedGame(5, 0);
        playFreeFirstRound(game);

        boolean forfeited = game.forfeitOnTimeIfExpired(T0.plusSeconds(5 * 60 - 1));

        assertFalse(forfeited);
        assertEquals(GameStatus.ONGOING, game.status());
    }

    @Test
    void forfeitOnTimeIfExpiredEndsTheGameOnTimeoutForTheRunningColor() {
        Game game = timedGame(5, 0);
        playFreeFirstRound(game);

        boolean forfeited = game.forfeitOnTimeIfExpired(T0.plusSeconds(5 * 60));

        assertTrue(forfeited);
        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.TIMEOUT, game.result().cause());
        assertEquals(Color.WHITE, game.result().winner());
        assertNull(game.clockRunningFor());
        assertEquals(0, game.millisRemaining(Color.BLACK));
    }

    @Test
    void clockDeadlineReflectsTheRunningColorsRemainingTime() {
        Game game = timedGame(5, 0);
        playFreeFirstRound(game);

        assertEquals(T0.plusSeconds(5 * 60), game.clockDeadline());
    }

    /**
     * Joue le premier round (gratuit, voir Game) pour amener la partie au round 2,
     * ou la pendule tourne reellement pour la premiere fois : coup blanc e2-e4 devine
     * faux, le trait (et la pendule) passe donc a BLACK, sans qu'aucun temps n'ait ete
     * decompte. Instant T0 fixe pour que les tests puissent raisonner sur des offsets
     * simples a partir de la (Game.clockRunningSince() vaudra T0 juste apres).
     */
    private static void playFreeFirstRound(Game game) {
        game.submitMove(findMove(game.legalMoves(), "e2", "e4"), T0);
        game.submitGuess(null, T0);
    }

    private static Game timedGame(int baseMinutes, int incrementSeconds) {
        return Game.newGame(GameId.random(), GameVariant.GUESSCHESS, TimeControl.of(baseMinutes, incrementSeconds));
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
