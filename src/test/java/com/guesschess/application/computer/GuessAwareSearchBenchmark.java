package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.rules.MoveGenerator;

/**
 * Micro-benchmark autonome (main, pas un test JUnit) du prototype GuessAwareSearch contre
 * NegamaxSearch : temps par (profondeur, guessPlies), JIT rechauffe par une passe a blanc.
 * Lancement : voir la note de la session (java -cp target/classes:target/test-classes ...).
 */
public final class GuessAwareSearchBenchmark {

    private GuessAwareSearchBenchmark() {
    }

    public static void main(String[] args) {
        Board start = Board.initial();
        Board middlegame = play(start, "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "c2c3", "g8f6", "d2d3", "d7d6");
        int maxDepth = args.length > 0 ? Integer.parseInt(args[0]) : 4;

        for (int warm = 0; warm < 3; warm++) {
            NegamaxSearch.searchRoot(middlegame, 3);
            new GuessAwareSearch().solveRoot(middlegame, 3, 3);
        }

        for (Object[] named : new Object[][]{{"depart", start}, {"milieu de partie", middlegame}}) {
            Board board = (Board) named[1];
            System.out.println("== " + named[0]);
            for (int depth = 2; depth <= maxDepth; depth++) {
                long t0 = System.nanoTime();
                NegamaxSearch.searchRoot(board, depth);
                double classicalMs = (System.nanoTime() - t0) / 1e6;
                System.out.printf("depth %d classique (alpha-beta)         : %8.0f ms%n", depth, classicalMs);
                for (int guessPlies = 1; guessPlies <= depth; guessPlies++) {
                    GuessAwareSearch search = new GuessAwareSearch();
                    long start0 = System.nanoTime();
                    GuessAwareSearch.RootSolution solution = search.solveRoot(board, depth, guessPlies);
                    double ms = (System.nanoTime() - start0) / 1e6;
                    System.out.printf("depth %d guessPlies=%d (%5d noeuds jeu) : %8.0f ms  (x%.1f)  valeur=%d B=%d%n",
                            depth, guessPlies, search.guessNodes(), ms, ms / classicalMs,
                            solution.value(), solution.passScore());
                }
            }
        }
    }

    private static Board play(Board board, String... moves) {
        Board current = board;
        for (String uci : moves) {
            Board from = current;
            Move move = MoveGenerator.generateLegalMoves(from, from.sideToMove()).stream()
                    .filter(m -> (m.from().toString() + m.to()).equals(uci))
                    .findFirst().orElseThrow(() -> new IllegalStateException("illegal " + uci));
            current = from.applyMove(move);
        }
        return current;
    }
}
