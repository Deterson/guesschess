package com.guesschess.domain.game;

import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trip toMemento/fromMemento (reserve a la persistance, etape 4 de la roadmap) :
 * une partie reconstruite depuis son memento doit se comporter a l'identique.
 */
class GameMementoTest {

    @Test
    void freshGameSurvivesARoundTrip() {
        Game original = Game.newGame();

        Game reconstructed = Game.fromMemento(original.toMemento());

        assertEquals(original.id(), reconstructed.id());
        assertEquals(original.board(), reconstructed.board());
        assertEquals(original.status(), reconstructed.status());
        assertEquals(original.legalMoves(), reconstructed.legalMoves());
    }

    @Test
    void gameWithPlayedMovesSurvivesARoundTrip() {
        Game original = Game.newGame();
        play(original, "e2", "e4");
        play(original, "e7", "e5");

        Game reconstructed = Game.fromMemento(original.toMemento());

        assertEquals(original.board(), reconstructed.board());
        assertEquals(original.moveHistory(), reconstructed.moveHistory());
        assertEquals(original.sideToMove(), reconstructed.sideToMove());
    }

    @Test
    void gameWithAPendingMoveAwaitingAGuessSurvivesARoundTrip() {
        Game original = Game.newGame();
        Move e4 = findMove(original.legalMoves(), "e2", "e4");
        original.submitMove(e4);

        Game reconstructed = Game.fromMemento(original.toMemento());
        RoundResult result = reconstructed.submitGuess(null).orElseThrow();

        assertEquals(e4, result.actualMove());
        assertEquals(GameStatus.ONGOING, reconstructed.status());
    }

    @Test
    void finishedGameSurvivesARoundTripWithItsResult() {
        Game original = Game.newGame();
        play(original, "f2", "f3");
        play(original, "e7", "e5");
        play(original, "g2", "g4");
        Move mate = findMove(original.legalMoves(), "d8", "h4");
        original.submitMove(mate);
        original.submitGuess(null);

        Game reconstructed = Game.fromMemento(original.toMemento());

        assertEquals(GameStatus.FINISHED, reconstructed.status());
        assertEquals(original.result(), reconstructed.result());
        assertEquals(GameResultCause.CHECKMATE, reconstructed.result().cause());
    }

    @Test
    void timedGameWithARunningClockSurvivesARoundTrip() {
        TimeControl timeControl = TimeControl.of(5, 3);
        Game original = Game.newGame(GameId.random(), GameVariant.GUESSCHESS, timeControl);
        play(original, "e2", "e4"); // round 1, gratuit - fait passer le trait (et la pendule) a BLACK
        original.submitMove(findMove(original.legalMoves(), "e7", "e5")); // round 2 : la pendule tourne pour de vrai

        Game reconstructed = Game.fromMemento(original.toMemento());

        assertEquals(original.timeControl(), reconstructed.timeControl());
        assertEquals(original.clockRunningFor(), reconstructed.clockRunningFor());
        assertEquals(original.clockRunningSince(), reconstructed.clockRunningSince());
        assertEquals(original.millisRemaining(Color.WHITE), reconstructed.millisRemaining(Color.WHITE));
        assertEquals(original.millisRemaining(Color.BLACK), reconstructed.millisRemaining(Color.BLACK));
    }

    private static void play(Game game, String from, String to) {
        game.submitMove(findMove(game.legalMoves(), from, to));
        game.submitGuess(null);
    }

    private static Move findMove(List<Move> moves, String from, String to) {
        var fromPos = com.guesschess.domain.board.Position.fromAlgebraic(from);
        var toPos = com.guesschess.domain.board.Position.fromAlgebraic(to);
        return moves.stream()
                .filter(m -> m.from().equals(fromPos) && m.to().equals(toPos))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no legal move " + from + "-" + to));
    }
}
