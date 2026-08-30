package com.guesschess.domain.pggn;

import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Parsing PGGN (etape 10 de la roadmap) : extraction simple, sans revalidation contre
 * le moteur de regles (voir PggnParser). Verifie a la fois sur un texte ecrit a la
 * main et en aller-retour avec PggnWriter.
 */
class PggnParserTest {

    @Test
    void parsesHeadersAndPlainMovesWithoutGuesses() {
        String text = """
                [Event "?"]
                [Date "?"]
                [White "?"]
                [Black "?"]
                [Variant "GUESSCHESS"]
                [Result "*"]
                [Termination "?"]

                1. e4 e5 2. Nf3
                """;

        PggnGame parsed = PggnParser.parse(text);

        assertEquals("GUESSCHESS", parsed.tags().get("Variant"));
        assertEquals("*", parsed.tags().get("Result"));
        assertEquals(3, parsed.plies().size());

        assertEquals(new PggnPly(1, Color.WHITE, "e4", null), parsed.plies().get(0));
        assertEquals(new PggnPly(1, Color.BLACK, "e5", null), parsed.plies().get(1));
        assertEquals(new PggnPly(2, Color.WHITE, "Nf3", null), parsed.plies().get(2));
    }

    @Test
    void parsesWrongGuessesInParentheses() {
        PggnGame parsed = PggnParser.parse("[Result \"*\"]\n\n1. e4(e3) e5(Nc6)");

        assertEquals(new PggnPly(1, Color.WHITE, "e4", "e3"), parsed.plies().get(0));
        assertEquals(new PggnPly(1, Color.BLACK, "e5", "Nc6"), parsed.plies().get(1));
    }

    @Test
    void parsesACancelledRoundAsGuessOnlyWithNoRealMove() {
        PggnGame parsed = PggnParser.parse("[Result \"*\"]\n\n2. (Nf3) Nc6(a5)");

        PggnPly whitePly = parsed.plies().get(0);
        assertNull(whitePly.realSan());
        assertEquals("Nf3", whitePly.guessedSan());

        PggnPly blackPly = parsed.plies().get(1);
        assertEquals("Nc6", blackPly.realSan());
        assertEquals("a5", blackPly.guessedSan());
    }

    @Test
    void parsesTheGuessmateHardcodedMateSuffix() {
        PggnGame parsed = PggnParser.parse("[Result \"0-1\"]\n\n16. (Ke2)#");

        PggnPly ply = parsed.plies().get(0);
        assertEquals(16, ply.moveNumber());
        assertNull(ply.realSan());
        assertEquals("Ke2#", ply.guessedSan());
    }

    @Test
    void roundTripsWithPggnWriter() {
        Game game = Game.newGame();
        submitRound(game, "e2", "e4", "e2", "e3");
        submitRound(game, "e7", "e5", "b8", "c6");
        submitRound(game, "g1", "f3", "g1", "f3");
        submitRound(game, "b8", "c6", "a7", "a5");

        PggnGame written = PggnWriter.toPggnGame(game, java.util.Map.of());
        String text = PggnWriter.render(written);
        PggnGame reparsed = PggnParser.parse(text);

        assertEquals(written.tags(), reparsed.tags());
        assertEquals(written.plies(), reparsed.plies());
    }

    private static void submitRound(Game game, String moveFrom, String moveTo, String guessFrom, String guessTo) {
        List<Move> legalMoves = game.legalMoves();
        Move move = findMove(legalMoves, moveFrom, moveTo);
        Move guess = guessFrom == null ? null : findMove(legalMoves, guessFrom, guessTo);
        game.submitMove(move);
        game.submitGuess(guess);
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
