package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.ZobristHash;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MaterialEvaluator;
import com.guesschess.domain.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recherche "guess-aware a chaque noeud" (etape 21, agent guessaware@1) - en parallele de
 * NegamaxSearch, qu'elle utilise comme recherche classique sous les guessPlies premiers plis.
 *
 * Chaque noeud (joueur M au trait) est traite comme un petit jeu matriciel a deux joueurs
 * simultanes, exactement comme la regle du jeu : M choisit un coup m, l'adversaire O
 * devine un coup g parmi les coups legaux de M.
 *  - g != m : m est joue, valeur A[m] = -valeur(apres m) pour M ;
 *  - g == m : le coup est annule (Board.pass(), aucune piece ne bouge, O prend le trait),
 *    valeur B = -valeur(pass) pour M - la MEME pour tous les coups m, puisque le plateau
 *    apres annulation est toujours le meme.
 * Le gain de M est donc sum(x[m]*A[m]) - max_g(x[g]*(A[g]-B)) pour une strategie mixte x :
 * la valeur du noeud est le maximum de cette expression sur x. Comme B ne depend pas de m, le
 * passage n'ajoute qu'UN enfant de plus par noeud (b+1 au lieu de b).
 * La valeur se calcule aussi cote O (dual) : y[m] = (A[m]-V)/(A[m]-B) pour les coups meilleurs
 * que V - la distribution de devinette qui empeche M de gagner plus que V (voir solve).
 *
 * Contrairement a l'alpha-beta, la valeur exacte d'un noeud depend de TOUS les A[m] : pas de
 * coupure sur les guessPlies premiers plis, sauf la fenetre du dernier pli (voir playedScores).
 *
 * Regles d'echec (Rules) : GUESSMATE - etre devine en echec = defaite immediate (B = -MATE) ;
 * GUESSCHESS - la recursion par pass modelise le round annule qui laisse le roi en echec (l'autre
 * camp peut alors capturer le roi, KING_CAPTURE_SCORE), et fast_mate (Game.FAST_MATE_ENABLED) :
 * au trait, en echec, avec un seul coup legal = defaite (nulle si materiel insuffisant).
 * Limites : fast_mate n'est vu que sur les guessPlies premiers plis (la recherche classique en
 * dessous l'ignore) ; un pass consomme un pli de profondeur comme un vrai coup.
 */
public final class GuessAwareSearch {

    private static final int INFINITY = 1_000_000;
    private static final int NOT_MODELLED = Integer.MIN_VALUE;

    /**
     * @param guessedInCheckLoses vrai (GUESSMATE) : deviner correctement le coup d'un joueur en
     *                            echec gagne la partie
     * @param fastMate vrai (GUESSCHESS avec Game.FAST_MATE_ENABLED) : voir la description de la classe
     */
    public record Rules(boolean guessedInCheckLoses, boolean fastMate) {
        public static Rules forVariant(GameVariant variant) {
            return new Rules(variant == GameVariant.GUESSMATE,
                    variant == GameVariant.GUESSCHESS && Game.isFastMateEnabled());
        }
    }

    /** Depassement du budget de temps (voir withDeadline) - sans pile d'appels, il sert de signal. */
    public static final class SearchTimeoutException extends RuntimeException {
        SearchTimeoutException() {
            super("guess-aware search timed out", null, false, false);
        }
    }

    private final Rules rules;
    private final boolean windowLastPly;
    private long deadlineNanos = Long.MAX_VALUE;

    /** Nombre de noeuds internes resolus par le jeu matriciel (pour le benchmark). */
    private long guessNodes;

    /**
     * Table de transposition (etape 24) pour les delegations classiques (guessPlies == 0,
     * voir value() et la branche fenetree de playedScores) - une instance par recherche
     * (cette classe est deja instanciee fraiche a chaque decision, voir GuessAwareAgent),
     * partagee entre tous les appels a NegamaxSearch.negamax faits par CETTE recherche.
     */
    private final TranspositionTable classicalTt = new TranspositionTable();

    /** Tri killer/historique (etape 25) pour ces memes delegations classiques - voir SearchHeuristics. */
    private final SearchHeuristics classicalHeuristics = new SearchHeuristics();

    /**
     * Cle de cache d'un noeud "jeu matriciel" (guessPlies > 0) : hash Zobrist + profondeur
     * + guessPlies restants. guessPlies ne decroit pas toujours en lockstep avec depth
     * (guessedScore plafonne passGuessPlies a 1 via Math.max, voir plus bas) - deux chemins
     * peuvent donc atteindre la meme position a la meme profondeur avec un guessPlies
     * different ; les distinguer evite de confondre un noeud classique (delegue a
     * NegamaxSearch, cle differente par construction : table separee) avec un noeud
     * matriciel, et deux noeuds matriciels entre eux (voir CLAUDE.md, etape 24).
     */
    private record NodeKey(long hash, int depth, int guessPlies) {
    }

    /** Cache des valeurs de noeud "jeu matriciel" deja resolues par cette recherche (voir NodeKey). */
    private final Map<NodeKey, Integer> nodeCache = new HashMap<>();

    public GuessAwareSearch(Rules rules) {
        this(rules, true);
    }

    /** GUESSCHESS sans fast_mate. */
    public GuessAwareSearch() {
        this(new Rules(false, false));
    }

    /** windowLastPly : faux desactive l'optimisation de fenetre (reference exacte, pour les tests). */
    GuessAwareSearch(Rules rules, boolean windowLastPly) {
        this.rules = rules;
        this.windowLastPly = windowLastPly;
    }

    /** Abandonne (SearchTimeoutException) toute recherche encore en cours a cet instant (System.nanoTime). */
    public GuessAwareSearch withDeadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
        return this;
    }

    public long guessNodes() {
        return guessNodes;
    }

    /** Statistiques de cache pour le benchmark (etape 24) - jamais consultees par la recherche elle-meme. */
    TranspositionTable classicalTranspositions() {
        return classicalTt;
    }

    long nodeCacheSize() {
        return nodeCache.size();
    }

    /**
     * @param playedScore valeur (point de vue de M) du coup s'il est reellement joue
     * @param probability part du coup dans la strategie mixte d'equilibre de M (jouer)
     * @param guessProbability part du coup dans la strategie d'equilibre de O (deviner)
     */
    public record RootMove(Move move, int playedScore, double probability, double guessProbability) {
    }

    /**
     * @param value valeur d'equilibre de la position pour le joueur au trait
     * @param passScore valeur si son coup est devine et annule (B)
     * @param moves tous les coups legaux, tries par probabilite decroissante
     */
    public record RootSolution(int value, int passScore, List<RootMove> moves) {
    }

    /** Valeur d'un jeu matriciel, strategie mixte de M (x) et de devinette de O (y), meme ordre que A. */
    record Solution(double value, double[] mix, double[] guess) {
    }

    public RootSolution solveRoot(Board board, int depth, int guessPlies) {
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be >= 1, got " + depth);
        }
        Color color = board.sideToMove();
        List<Move> moves = NegamaxSearch.orderMoves(MoveGenerator.generateLegalMoves(board, color));
        if (moves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to search from");
        }
        int b = guessPlies > 0 ? guessedScore(board, color, depth, guessPlies) : NOT_MODELLED;
        int[] a = playedScores(board, moves, depth, guessPlies, b);
        Solution solution = b == NOT_MODELLED ? pureBest(a) : solveMix(a, b);
        List<RootMove> result = new ArrayList<>(moves.size());
        for (int i = 0; i < moves.size(); i++) {
            result.add(new RootMove(moves.get(i), a[i], solution.mix()[i], solution.guess()[i]));
        }
        result.sort(Comparator.comparingDouble(RootMove::probability).reversed()
                .thenComparing(Comparator.comparingInt(RootMove::playedScore).reversed()));
        return new RootSolution((int) Math.round(solution.value()), b == NOT_MODELLED ? 0 : b, result);
    }

    /**
     * Valeur du noeud du point de vue du joueur au trait. Memoisee par NodeKey (etape 24) -
     * un hit renvoie directement la valeur deja resolue par cette meme recherche sans
     * jamais changer le resultat (aucune fenetre alpha/beta a ce niveau, contrairement a
     * NegamaxSearch : la valeur d'un noeud matriciel ne depend jamais du contexte d'appel,
     * seulement de (position, depth, guessPlies), donc toujours exacte, voir la javadoc de
     * la classe).
     */
    int value(Board board, int depth, int guessPlies) {
        if (guessPlies <= 0) {
            return NegamaxSearch.negamax(board, depth, -INFINITY, INFINITY, classicalTt, classicalHeuristics);
        }
        long hash = ZobristHash.hash(board);
        NodeKey key = new NodeKey(hash, depth, guessPlies);
        Integer cached = nodeCache.get(key);
        if (cached != null) {
            return cached;
        }
        Color color = board.sideToMove();
        List<Move> moves = NegamaxSearch.orderMoves(MoveGenerator.generateLegalMoves(board, color));
        boolean inCheck = CheckDetector.isInCheck(board, color);
        int result;
        if (moves.isEmpty()) {
            result = inCheck ? -(NegamaxSearch.MATE_SCORE + depth) : 0;
        } else if (inCheck && rules.fastMate() && moves.size() == 1) {
            result = MaterialEvaluator.isInsufficientMaterial(board) ? 0 : -(NegamaxSearch.MATE_SCORE + depth);
        } else if (depth == 0) {
            result = NegamaxSearch.perspectiveEval(board);
        } else {
            int b = guessedScore(board, color, depth, guessPlies);
            int[] a = playedScores(board, moves, depth, guessPlies, b);
            guessNodes++;
            result = (int) Math.round(solve(a, b));
        }
        nodeCache.put(key, result);
        return result;
    }

    /**
     * A[] de chaque coup (moves deja tries, meilleurs candidats d'abord). Quand les enfants
     * sont classiques (guessPlies == 1) et que B est connu, seule la valeur exacte des
     * coups meilleurs que B (d > 0) et du meilleur coup non positif compte pour solve() :
     * les autres sont domines (etre devine ne leur coute rien, mais un meilleur coup
     * "gratuit" existe deja). On les cherche donc avec une fenetre alpha-beta dont la
     * borne est le meilleur coup non positif deja trouve - un score renvoye hors fenetre
     * n'est qu'une borne superieure, sans effet sur la valeur du noeud.
     */
    private int[] playedScores(Board board, List<Move> moves, int depth, int guessPlies, int b) {
        boolean windowed = windowLastPly && guessPlies == 1 && b != NOT_MODELLED;
        int bestNonPositive = -INFINITY;
        int[] a = new int[moves.size()];
        for (int i = 0; i < a.length; i++) {
            checkDeadline();
            Move move = moves.get(i);
            if (move.isCapture() && move.capturedPiece().type() == PieceType.KING) {
                a[i] = NegamaxSearch.KING_CAPTURE_SCORE;
            } else if (windowed) {
                a[i] = -NegamaxSearch.negamax(board.applyMove(move), depth - 1, -INFINITY, -bestNonPositive, classicalTt, classicalHeuristics);
                if (a[i] <= b && a[i] > bestNonPositive) {
                    bestNonPositive = a[i];
                }
            } else {
                a[i] = -value(board.applyMove(move), depth - 1, guessPlies - 1);
            }
        }
        return a;
    }

    private void checkDeadline() {
        if (System.nanoTime() > deadlineNanos) {
            throw new SearchTimeoutException();
        }
    }

    /**
     * Valeur B (point de vue de M) si son coup est devine. GUESSMATE en echec : defaite
     * immediate. Sinon le pass est cherche comme n'importe quel coup - y compris en echec en
     * GUESSCHESS : l'adversaire a alors le trait avec le roi de M en prise (voir la description
     * de la classe), la profondeur tronquant le cycle "devine -> capture devinee -> ...".
     */
    private int guessedScore(Board board, Color color, int depth, int guessPlies) {
        boolean inCheck = CheckDetector.isInCheck(board, color);
        if (rules.guessedInCheckLoses() && inCheck) {
            return -NegamaxSearch.MATE_SCORE;
        }
        // GUESSCHESS en echec : le noeud apres pass a toujours besoin d'etre resolu comme jeu
        // matriciel (au moins un pli guess-aware, meme si guessPlies est epuise) - la capture du
        // roi y est LE coup evident, donc devine, et l'annule. Recherche classique, elle y verrait
        // une victoire certaine (KING_CAPTURE_SCORE) : tout echec vaudrait un mat, comme en GUESSMATE.
        int passGuessPlies = inCheck ? Math.max(guessPlies - 1, 1) : guessPlies - 1;
        return -value(board.pass(), depth - 1, passGuessPlies);
    }

    private static Solution pureBest(int[] a) {
        int best = 0;
        for (int i = 1; i < a.length; i++) {
            if (a[i] > a[best]) {
                best = i;
            }
        }
        double[] mix = new double[a.length];
        mix[best] = 1;
        return new Solution(a[best], mix, mix.clone());
    }

    /**
     * Valeur d'equilibre du jeu matriciel decrit plus haut, cote O (dual) : O choisit une
     * distribution de devinette y minimisant max_m (a[m] - y[m]*d[m]), avec d[m] = a[m] - b -
     * la valeur V est la plus petite valeur >= (meilleur coup non positif) telle que
     * f(V) = somme sur d[m] > 0, a[m] > V de (a[m]-V)/d[m] <= 1. f decroit de facon continue :
     * bissection. Un seul coup legal : la devinette est forcee, valeur b. Equivalent, et verifie
     * comme tel dans les tests, au maximum sur x de sum(x*a) - max_g(x[g]*d[g]) (voir solveMix).
     */
    static double solve(int[] a, int b) {
        int n = a.length;
        if (n == 1) {
            return b;
        }
        double maxA = Double.NEGATIVE_INFINITY;
        double maxNonPositive = Double.NEGATIVE_INFINITY;
        boolean anyPositive = false;
        for (int value : a) {
            maxA = Math.max(maxA, value);
            if (value - b > 0) {
                anyPositive = true;
            } else {
                maxNonPositive = Math.max(maxNonPositive, value);
            }
        }
        if (!anyPositive) {
            return allNonPositiveValue(a, b, maxA);
        }
        double lo = maxNonPositive > Double.NEGATIVE_INFINITY ? maxNonPositive : b;
        if (guessLoad(a, b, lo) <= 1) {
            return lo;
        }
        double hi = maxA;
        for (int iteration = 0; iteration < 80; iteration++) {
            double mid = (lo + hi) / 2;
            if (guessLoad(a, b, mid) > 1) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    /**
     * Aucun coup ne vaut plus que le pass (zugzwang) : M prefere alors etre devine, et O, oblige
     * de deviner un coup legal, doit repartir sa devinette. y[m] <= (V-a[m])/(b-a[m]) pour que
     * jouer m ne rapporte pas plus que V ; V = max des a tant que ces plafonds suffisent a placer
     * toute la devinette (somme >= 1), sinon V monte jusqu'a ce qu'ils somment a 1. Un coup qui
     * vaut exactement le pass (loss == 0) absorbe toute la devinette sans rien couter a O.
     */
    private static double allNonPositiveValue(int[] a, int b, double maxA) {
        double load = 0;
        double sumInverse = 0;
        double weighted = 0;
        for (int value : a) {
            double loss = (double) b - value;
            if (loss == 0) {
                return maxA;
            }
            load += (maxA - value) / loss;
            sumInverse += 1 / loss;
            weighted += value / loss;
        }
        return load >= 1 ? maxA : (1 + weighted) / sumInverse;
    }

    /** f(V) = somme, sur les coups meilleurs que le pass (d > 0) et que V, de (a-V)/d. */
    private static double guessLoad(int[] a, int b, double v) {
        double load = 0;
        for (int value : a) {
            double d = (double) value - b;
            if (d > 0 && value > v) {
                load += (value - v) / d;
            }
        }
        return load;
    }

    /**
     * Comme solve, plus les deux strategies d'equilibre : x pour M (jouer) et y pour O (deviner).
     * x : recherche ternaire du plafond t de penalite - x[m] <= t/d[m] (aucune borne si d[m] <= 0),
     * remplissage glouton des meilleurs a[m], gain moins t, concave en t. y[m] = (a[m]-V)/d[m] sur
     * les coups meilleurs que le pass et que V, la masse restante (V plafonne par un coup non
     * positif, pas par la devinette) allant a ces memes coups (voir spreadLeftover). Aucun coup
     * meilleur que le pass : voir allNonPositiveMix.
     */
    static Solution solveMix(int[] a, int b) {
        int n = a.length;
        if (n == 1) {
            return new Solution(b, new double[]{1}, new double[]{1});
        }
        double value = solve(a, b);
        double[] d = new double[n];
        boolean anyNonPositive = false;
        double sumInverse = 0;
        double maxD = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            d[i] = (double) a[i] - b;
            maxD = Math.max(maxD, d[i]);
            if (d[i] <= 0) {
                anyNonPositive = true;
            } else {
                sumInverse += 1 / d[i];
            }
        }
        if (maxD <= 0) {
            return allNonPositiveMix(a, b, value);
        }
        Integer[] byScore = new Integer[n];
        for (int i = 0; i < n; i++) {
            byScore[i] = i;
        }
        Arrays.sort(byScore, (i, j) -> Integer.compare(a[j], a[i]));

        double lo = anyNonPositive ? 0 : 1 / sumInverse;
        double hi = maxD;
        for (int iteration = 0; iteration < 60; iteration++) {
            double m1 = lo + (hi - lo) / 3;
            double m2 = hi - (hi - lo) / 3;
            if (gain(a, d, byScore, m1, null) < gain(a, d, byScore, m2, null)) {
                lo = m1;
            } else {
                hi = m2;
            }
        }
        double[] mix = new double[n];
        gain(a, d, byScore, (lo + hi) / 2, mix);

        double[] guess = new double[n];
        double guessed = 0;
        for (int i = 0; i < n; i++) {
            if (d[i] > 0 && a[i] > value) {
                guess[i] = (a[i] - value) / d[i];
                guessed += guess[i];
            }
        }
        double leftover = Math.max(0, 1 - guessed);
        if (leftover > 0) {
            spreadLeftover(guess, leftover, mix, d, a);
        }
        return new Solution(value, mix, guess);
    }

    /**
     * Masse de devinette restante (V plafonne par un coup non positif, pas par la devinette) :
     * uniquement sur des coups meilleurs que le pass, ou la placer ne peut que baisser leur gain -
     * proportionnellement a x, sinon sur le meilleur d'entre eux.
     */
    private static void spreadLeftover(double[] guess, double leftover, double[] mix, double[] d, int[] a) {
        double mixOnPositive = 0;
        int bestPositive = -1;
        for (int i = 0; i < guess.length; i++) {
            if (d[i] > 0) {
                mixOnPositive += mix[i];
                if (bestPositive < 0 || a[i] > a[bestPositive]) {
                    bestPositive = i;
                }
            }
        }
        for (int i = 0; i < guess.length; i++) {
            if (d[i] > 0) {
                guess[i] += mixOnPositive > 1e-12 ? leftover * mix[i] / mixOnPositive : (i == bestPositive ? leftover : 0);
            }
        }
    }

    /** Equilibre quand aucun coup ne vaut plus que le pass, voir allNonPositiveValue. */
    private static Solution allNonPositiveMix(int[] a, int b, double value) {
        int n = a.length;
        double maxA = Double.NEGATIVE_INFINITY;
        for (int v : a) {
            maxA = Math.max(maxA, v);
        }
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            if (a[i] == b) {
                x = pureBest(a).mix();
                y[i] = 1;
                return new Solution(value, x, y);
            }
        }
        double load = 0;
        double sumInverse = 0;
        for (int v : a) {
            load += (maxA - v) / ((double) b - v);
            sumInverse += 1 / ((double) b - v);
        }
        if (load >= 1) {
            x = pureBest(a).mix();
            for (int i = 0; i < n; i++) {
                y[i] = ((maxA - a[i]) / ((double) b - a[i])) / load;
            }
        } else {
            for (int i = 0; i < n; i++) {
                double loss = (double) b - a[i];
                x[i] = (1 / loss) / sumInverse;
                y[i] = (value - a[i]) / loss;
            }
        }
        return new Solution(value, x, y);
    }

    /** Gain sous plafond t (meilleur remplissage possible), -infini si infaisable. */
    private static double gain(int[] a, double[] d, Integer[] byScore, double t, double[] mixOut) {
        double remaining = 1;
        double expected = 0;
        for (int index : byScore) {
            double cap = d[index] > 0 ? Math.min(1, t / d[index]) : 1;
            double take = Math.min(cap, remaining);
            expected += take * a[index];
            remaining -= take;
            if (mixOut != null) {
                mixOut[index] = take;
            }
            if (remaining <= 1e-9) {
                return expected - t;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** Valeur primale de x contre sa meilleure devinette : sum(x*a) - max_g(x[g]*d[g]). */
    static double primalValue(int[] a, int b, double[] x) {
        double expected = 0;
        double penalty = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.length; i++) {
            expected += x[i] * a[i];
            penalty = Math.max(penalty, x[i] * ((double) a[i] - b));
        }
        return expected - penalty;
    }
}
