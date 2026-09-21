package com.guesschess.application.computer;

import com.guesschess.application.computer.GuessAwareSearch.RootMove;
import com.guesschess.application.computer.GuessAwareSearch.RootSolution;
import com.guesschess.application.computer.GuessAwareSearch.Rules;
import com.guesschess.application.computer.GuessAwareSearch.Solution;
import com.guesschess.application.computer.NegamaxSearch.ScoredMove;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuessAwareSearchTest {

    private static Position at(String algebraic) {
        return Position.fromAlgebraic(algebraic);
    }

    private static Piece piece(PieceType type, Color color) {
        return Piece.of(type, color);
    }

    private static RootMove find(RootSolution solution, String from, String to) {
        return solution.moves().stream()
                .filter(m -> m.move().from().equals(at(from)) && m.move().to().equals(at(to)))
                .findFirst().orElseThrow();
    }

    @Test
    void singleLegalMoveIsAlwaysGuessed() {
        assertEquals(-30, GuessAwareSearch.solve(new int[]{500}, -30), 1e-6);
    }

    @Test
    void whenPlayingIsNeverWorseThanPassingTheBestMoveIsPlayedPurely() {
        // Tous les coups valent au moins ce que vaut un pass : etre devine ne peut que aider.
        Solution solution = GuessAwareSearch.solveMix(new int[]{10, -40, -80}, 50);

        assertEquals(10, solution.value(), 1e-6);
        assertEquals(1.0, solution.mix()[0], 1e-6);
    }

    /**
     * Deux coups gagnants de 100 et 60 face a un pass qui vaut 0 : jouer toujours le
     * meilleur se fait deviner (valeur 0), la strategie d'equilibre equilibre les deux
     * penalites x*d : t = 1/(1/100 + 1/60) = 37,5, x = (0,375 ; 0,625), valeur 37,5. Cote
     * devineur, y = (a-V)/d = (0,625 ; 0,375) : deviner plus souvent le coup le plus fort.
     */
    @Test
    void mixesObviousMovesInverselyToTheirGainOverPassing() {
        Solution solution = GuessAwareSearch.solveMix(new int[]{100, 60}, 0);

        assertEquals(37.5, solution.value(), 0.01);
        assertEquals(0.375, solution.mix()[0], 0.001);
        assertEquals(0.625, solution.mix()[1], 0.001);
        assertEquals(0.625, solution.guess()[0], 0.001);
        assertEquals(0.375, solution.guess()[1], 0.001);
    }

    /**
     * Cote joueur (primal, recherche ternaire de x) et cote devineur (dual, bissection sur V)
     * doivent donner la meme valeur d'equilibre, et chaque distribution somme a 1.
     */
    @Test
    void primalAndDualSolversAgreeOnRandomGames() {
        Random random = new Random(42);
        for (int game = 0; game < 300; game++) {
            int n = 2 + random.nextInt(12);
            int[] a = new int[n];
            for (int i = 0; i < n; i++) {
                a[i] = random.nextInt(2001) - 1000;
            }
            int b = random.nextInt(1001) - 500;

            Solution solution = GuessAwareSearch.solveMix(a, b);

            assertEquals(GuessAwareSearch.solve(a, b), solution.value(), 1e-9);
            assertEquals(solution.value(), GuessAwareSearch.primalValue(a, b, solution.mix()), 0.05,
                    "primal vs dual, a=" + java.util.Arrays.toString(a) + " b=" + b);
            assertEquals(1.0, java.util.Arrays.stream(solution.mix()).sum(), 1e-6);
            assertEquals(1.0, java.util.Arrays.stream(solution.guess()).sum(), 1e-6);
        }
    }

    /**
     * Zugzwang : jouer vaut moins que passer (b = 180) pour les deux coups. M veut etre devine, mais
     * O doit deviner un coup legal : il repartit y pour que M soit indifferent. Ni "jouer le meilleur
     * coup" (-136) ni "O ne devine jamais" ne tiennent : V = -12,4, x = (0,609 ; 0,391).
     */
    @Test
    void whenEveryMoveIsWorseThanPassingTheGuesserMustSpreadHisGuess() {
        Solution solution = GuessAwareSearch.solveMix(new int[]{-136, -312}, 180);

        assertEquals(-12.4, solution.value(), 0.1);
        assertEquals(0.609, solution.mix()[0], 0.001);
        assertEquals(0.391, solution.guess()[0], 0.001);
        assertEquals(solution.value(), GuessAwareSearch.primalValue(new int[]{-136, -312}, 180, solution.mix()), 0.05);
    }

    /**
     * Verification independante de la formule : la valeur du jeu est min sur y de max sur m du
     * gain de m, calculee par balayage brut (grille fine) sur des jeux a 2 et 3 coups, y compris
     * les cas ou tous les coups valent moins que le pass.
     */
    @Test
    void equilibriumValueMatchesABruteForceSearchOverGuessDistributions() {
        Random random = new Random(11);
        for (int game = 0; game < 200; game++) {
            int n = 2 + random.nextInt(2);
            int[] a = new int[n];
            for (int i = 0; i < n; i++) {
                a[i] = random.nextInt(2001) - 1000;
            }
            int b = game % 3 == 0 ? 1000 + random.nextInt(500) : random.nextInt(1001) - 500;

            double brute = Double.POSITIVE_INFINITY;
            int steps = n == 2 ? 4000 : 400;
            for (int i = 0; i <= steps; i++) {
                for (int j = 0; j <= (n == 2 ? 0 : steps - i); j++) {
                    double[] y = n == 2 ? new double[]{(double) i / steps, 1 - (double) i / steps}
                            : new double[]{(double) i / steps, (double) j / steps, 1 - (double) (i + j) / steps};
                    double worst = Double.NEGATIVE_INFINITY;
                    for (int m = 0; m < n; m++) {
                        worst = Math.max(worst, a[m] - y[m] * (a[m] - b));
                    }
                    brute = Math.min(brute, worst);
                }
            }

            assertEquals(brute, GuessAwareSearch.solve(a, b), 8.0, "a=" + java.util.Arrays.toString(a) + " b=" + b);
        }
    }

    /** Contre la strategie y d equilibre, aucun coup de M ne rapporte plus que V. */
    @Test
    void guessDistributionCapsEveryMoveAtTheEquilibriumValue() {
        Random random = new Random(7);
        for (int game = 0; game < 300; game++) {
            int n = 2 + random.nextInt(12);
            int[] a = new int[n];
            for (int i = 0; i < n; i++) {
                a[i] = random.nextInt(2001) - 1000;
            }
            int b = random.nextInt(1001) - 500;

            Solution solution = GuessAwareSearch.solveMix(a, b);

            for (int m = 0; m < n; m++) {
                double payoff = a[m] - solution.guess()[m] * (a[m] - b);
                assertTrue(payoff <= solution.value() + 1e-6, "move " + m + " pays " + payoff + " > V=" + solution.value());
            }
        }
    }

    @Test
    void withoutGuessPliesMatchesClassicalNegamax() {
        Board board = Board.initial();

        RootSolution solution = new GuessAwareSearch().solveRoot(board, 3, 0);
        ScoredMove classicalBest = NegamaxSearch.searchRoot(board, 3).get(0);

        assertEquals(classicalBest.score(), solution.value());
        assertEquals(1.0, solution.moves().get(0).probability(), 1e-9);
    }

    /** La fenetre alpha-beta sur le dernier pli guess-aware ne doit changer aucune valeur. */
    @Test
    void windowedLastPlyGivesTheSameValuesAsTheExactSearch() {
        Board board = Board.initial();
        for (Rules rules : new Rules[]{new Rules(false, false), new Rules(true, false), new Rules(false, true)}) {
            for (int depth = 2; depth <= 3; depth++) {
                for (int guessPlies = 1; guessPlies <= depth; guessPlies++) {
                    RootSolution exact = new GuessAwareSearch(rules, false).solveRoot(board, depth, guessPlies);
                    RootSolution windowed = new GuessAwareSearch(rules, true).solveRoot(board, depth, guessPlies);
                    assertEquals(exact.value(), windowed.value(), rules + " depth " + depth + " guessPlies " + guessPlies);
                    assertEquals(exact.passScore(), windowed.passScore());
                }
            }
        }
    }

    /**
     * Meme position que NegamaxSearchTest.avoidsHangingTheQueenToAVisibleRecapture : Dxd8
     * gagne un cavalier mais perd la dame sur Txd8. Le negamax classique le note tres bas.
     * Mais Txd8 est LE coup evident de noir : blanc le devine, il est annule, noir reste
     * un cavalier en moins - donc le noeud de reponse noire vaut environ -cavalier au lieu
     * de +dame-cavalier, et Dxd8 devient jouable (profondeur 3 : Dxd8, reponse noire,
     * reponse blanche ; guessPlies 2 : racine + noeud de reponse noire).
     */
    @Test
    void rehabilitatesTheQueenSacrificeWhoseRecaptureIsObvious() {
        Board board = Board.empty()
                .withPiece(at("g1"), piece(PieceType.KING, Color.WHITE))
                .withPiece(at("d1"), piece(PieceType.QUEEN, Color.WHITE))
                .withPiece(at("h7"), piece(PieceType.KING, Color.BLACK))
                .withPiece(at("a8"), piece(PieceType.ROOK, Color.BLACK))
                .withPiece(at("d8"), piece(PieceType.KNIGHT, Color.BLACK));

        ScoredMove classical = NegamaxSearch.searchRoot(board, 3).stream()
                .filter(sm -> sm.move().from().equals(at("d1")) && sm.move().to().equals(at("d8")))
                .findFirst().orElseThrow();
        RootMove sacrifice = find(new GuessAwareSearch().solveRoot(board, 3, 2), "d1", "d8");

        assertTrue(classical.score() < -300, "classique: " + classical.score());
        assertTrue(sacrifice.playedScore() > 0, "guess-aware: " + sacrifice.playedScore());
    }

    /**
     * Roi noir h8, roi blanc f6, tour blanche a1 : Th1+ met le roi noir en echec avec un
     * seul coup legal (Rg8 - g7 est couvert par le roi blanc, h7 par la tour). fast_mate
     * (GUESSCHESS) : victoire immediate des la resolution du round suivant - donc une valeur
     * de mat, alors que sans cette regle ce n'est qu'un echec ordinaire sans gain.
     */
    @Test
    void fastMateTurnsACheckWithASingleReplyIntoAWin() {
        Board board = Board.empty()
                .withPiece(at("f6"), piece(PieceType.KING, Color.WHITE))
                .withPiece(at("a1"), piece(PieceType.ROOK, Color.WHITE))
                .withPiece(at("h8"), piece(PieceType.KING, Color.BLACK));

        RootMove withFastMate = find(new GuessAwareSearch(new Rules(false, true)).solveRoot(board, 2, 2), "a1", "h1");
        RootMove withoutFastMate = find(new GuessAwareSearch(new Rules(false, false)).solveRoot(board, 2, 2), "a1", "h1");

        assertTrue(NegamaxSearch.isMateScore(withFastMate.playedScore()), "fast_mate: " + withFastMate.playedScore());
        assertTrue(!NegamaxSearch.isMateScore(withoutFastMate.playedScore()), "sans: " + withoutFastMate.playedScore());
    }

    /** Sans materiel pour mater, le fast_mate est une nulle, pas une victoire (voir Game.applyFastMateIfApplicable). */
    @Test
    void fastMateIsADrawWhenTheOpponentCannotForceMate() {
        Board afterCheck = Board.empty()
                .withPiece(at("g6"), piece(PieceType.KING, Color.WHITE))
                .withPiece(at("e5"), piece(PieceType.BISHOP, Color.WHITE))
                .withPiece(at("h8"), piece(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);

        int value = new GuessAwareSearch(new Rules(false, true)).value(afterCheck, 2, 2);

        assertEquals(0, value);
    }

    /**
     * GUESSMATE : un joueur en echec deviné perd tout de suite. Position de la Th1+ ci-dessus,
     * noir a alors un seul coup legal (donc forcement devine) : valeur de defaite pour noir,
     * meme sans regle fast_mate.
     */
    @Test
    void inGuessmateBeingGuessedWhileInCheckLosesImmediately() {
        Board board = Board.empty()
                .withPiece(at("f6"), piece(PieceType.KING, Color.WHITE))
                .withPiece(at("a1"), piece(PieceType.ROOK, Color.WHITE))
                .withPiece(at("h8"), piece(PieceType.KING, Color.BLACK));

        RootMove rookCheck = find(new GuessAwareSearch(new Rules(true, false)).solveRoot(board, 2, 2), "a1", "h1");

        assertTrue(NegamaxSearch.isMateScore(rookCheck.playedScore()), "guessmate: " + rookCheck.playedScore());
    }

    @Test
    void refusesToSearchWithoutAnyLegalMove() {
        Board board = Board.empty()
                .withPiece(at("a8"), piece(PieceType.KING, Color.BLACK))
                .withPiece(at("c7"), piece(PieceType.QUEEN, Color.WHITE))
                .withPiece(at("b6"), piece(PieceType.KING, Color.WHITE))
                .withSideToMove(Color.BLACK);

        assertThrows(IllegalArgumentException.class, () -> new GuessAwareSearch().solveRoot(board, 2, 2));
    }

    @Test
    void anExpiredDeadlineAbortsTheSearch() {
        GuessAwareSearch search = new GuessAwareSearch().withDeadline(System.nanoTime() - 1);

        assertThrows(GuessAwareSearch.SearchTimeoutException.class, () -> search.solveRoot(Board.initial(), 3, 2));
    }
}
