package com.guesschess.tournament;

import com.guesschess.application.computer.AgentSession;
import com.guesschess.application.computer.AgentSessionContext;
import com.guesschess.application.computer.GuessAgent;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameResult;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.pggn.PggnWriter;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Joue UNE partie de bout en bout entre deux GuessAgent, sans tete (aucune dependance
 * Spring/BD, voir CLAUDE.md etape 22) : les deux "soumissions simultanees" de la regle de
 * devinette n'ont pas besoin d'etre reellement concurrentes ici, rien ne les observe avant
 * que les deux soient calculees - on interroge donc les deux agents en sequence puis on
 * soumet les deux resultats au domaine, qui resout le round normalement (Game.resolveRound).
 */
public final class HeadlessGameRunner {

    private HeadlessGameRunner() {
    }

    public static GameRecord play(RoundRobinScheduler.Fixture fixture, GameVariant variant, long seed,
                                   GuessAgent whiteAgent, GuessAgent blackAgent, int roundLimit, String machine) {
        Game game = Game.fromPosition(fixture.opening(), variant);
        String openingFen = fixture.opening().toFen();

        Map<Color, AgentSession> sessions = new EnumMap<>(Color.class);
        sessions.put(Color.WHITE, whiteAgent.newSession(new AgentSessionContext(
                Color.WHITE, variant, "tournament-game-" + fixture.gameIndex(), new Random(deriveSeed(seed, fixture.gameIndex(), Color.WHITE)))));
        sessions.put(Color.BLACK, blackAgent.newSession(new AgentSessionContext(
                Color.BLACK, variant, "tournament-game-" + fixture.gameIndex(), new Random(deriveSeed(seed, fixture.gameIndex(), Color.BLACK)))));

        Map<Color, Long> thinkNanos = new EnumMap<>(Color.class);
        thinkNanos.put(Color.WHITE, 0L);
        thinkNanos.put(Color.BLACK, 0L);
        Map<Color, int[]> guessStats = new EnumMap<>(Color.class);
        guessStats.put(Color.WHITE, new int[2]);
        guessStats.put(Color.BLACK, new int[2]);

        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();
        int roundCount = 0;
        boolean aborted = false;

        while (game.status() == GameStatus.ONGOING) {
            if (roundCount >= roundLimit) {
                aborted = true;
                break;
            }
            Color mover = game.sideToMove();
            Color guesser = mover.opposite();
            List<Move> legalMoves = game.legalMoves();
            Board board = game.board();

            long t0 = System.nanoTime();
            Move actualMove = sessions.get(mover).move(board, legalMoves);
            thinkNanos.merge(mover, System.nanoTime() - t0, Long::sum);

            long t1 = System.nanoTime();
            Move guess = sessions.get(guesser).guess(board, legalMoves);
            thinkNanos.merge(guesser, System.nanoTime() - t1, Long::sum);

            game.submitMove(actualMove);
            RoundResult result = game.submitGuess(guess).orElseThrow();

            sessions.get(Color.WHITE).onRoundResolved(result);
            sessions.get(Color.BLACK).onRoundResolved(result);

            int[] stats = guessStats.get(guesser);
            stats[0]++;
            if (result.guessedCorrectly()) {
                stats[1]++;
            }
            roundCount++;
        }

        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        GameResult gameResult = game.result();
        String resultTag = resultTag(gameResult, aborted);
        String winner = gameResult != null && !gameResult.isDraw() ? gameResult.winner().name() : null;
        String cause = aborted ? "ROUND_LIMIT" : (gameResult != null ? gameResult.cause().name() : null);

        String pggn = PggnWriter.write(game, Map.of(
                "Event", "guesschess-tournament",
                "White", fixture.white().label(),
                "Black", fixture.black().label()));

        int[] whiteGuesses = guessStats.get(Color.WHITE);
        int[] blackGuesses = guessStats.get(Color.BLACK);

        return new GameRecord(
                fixture.gameIndex(), seed, fixture.openingIndex(), openingFen, variant.name(),
                fixture.white().label(), fixture.black().label(),
                resultTag, winner, cause, aborted, roundCount,
                thinkNanos.get(Color.WHITE) / 1_000_000, thinkNanos.get(Color.BLACK) / 1_000_000,
                whiteGuesses[0], whiteGuesses[1], blackGuesses[0], blackGuesses[1],
                durationMillis, machine, startedAt.toString(), pggn);
    }

    private static long deriveSeed(long seed, int gameIndex, Color color) {
        long colorSalt = color == Color.WHITE ? 0x1L : 0x2L;
        return seed ^ (0x9E3779B97F4A7C15L * ((long) gameIndex * 2 + colorSalt));
    }

    private static String resultTag(GameResult result, boolean aborted) {
        if (aborted || result == null) {
            return "*";
        }
        if (result.isDraw()) {
            return "1/2-1/2";
        }
        return result.winner() == Color.WHITE ? "1-0" : "0-1";
    }
}
