package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.ZobristHash;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.move.MoveType;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MoveGenerator;
import com.guesschess.domain.rules.PositionEvaluator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recherche negamax + elagage alpha-beta, moteur maison (etape 17 de la roadmap, voir la
 * note de conception qui a precede cette classe) - remplace le role de Stockfish comme
 * "oracle d'evaluation" mais reste lui-meme totalement ignorant de la mecanique de
 * devinette : c'est un moteur d'echecs classique, pur (aucune dependance Spring, comme
 * MoveGenerator/CheckDetector), pas de process externe. La couche guess-aware
 * (MinimaxChessEngine) l'utilise comme brique de base, un peu comme StockfishChessEngine
 * utilisait le MultiPV de Stockfish.
 *
 * searchRoot() interroge chaque coup racine avec sa PROPRE fenetre alpha-beta complete
 * (pas de coupe entre coups racine, contrairement a un negamax "pur") - plus couteux
 * qu'une seule recherche a fenetre partagee, mais necessaire pour obtenir un score exact
 * de chaque candidat plutot qu'une simple borne : la couche guess-aware compare ces
 * scores entre eux (ecart avec le 2e meilleur coup, etc.), une borne ne suffirait pas.
 */
public final class NegamaxSearch {

    private NegamaxSearch() {
    }

    private static final int INFINITY = 1_000_000;

    /**
     * Valeur de score equivalente attribuee a un mat force, comme
     * StockfishChessEngine.MATE_SCORE_MAGNITUDE (meme convention, meme grandeur) - un mat
     * trouve avec depth plis de recherche encore disponibles (donc trouve "plus tot"
     * depuis la racine) obtient une magnitude legerement plus grande qu'un mat trouve
     * plus profondement, pour que searchRoot() prefere toujours le mat le plus rapide.
     */
    public static final int MATE_SCORE = 100_000;

    /** Un score dont la magnitude depasse ce seuil represente un mat force. */
    private static final int MATE_THRESHOLD = MATE_SCORE - 1_000;

    /**
     * Score attribue a un coup qui capture directement un roi - situation qui n'existe
     * jamais en echecs classiques (MoveGenerator.generateLegalMoves ne genere jamais de
     * coup laissant son propre roi en echec, donc aucune recursion interne de cette classe
     * ne peut spontanement produire un tel coup), mais que la mecanique de devinette rend
     * possible : un round annule qui laisse un roi en echec non resolu (voir CLAUDE.md,
     * Game.resolveRound) peut faire apparaitre une capture de roi parmi les coups legaux
     * du plateau RACINE fourni a searchRoot. Volontairement au-dela de tout score de mat
     * (MATE_SCORE + une profondeur bornee) pour ne jamais etre depasse par un "simple" mat
     * trouve par ailleurs. Sans ce court-circuit, jouer ce coup produirait un plateau sans
     * roi que MoveGenerator/CheckDetector ne savent pas interpreter
     * (CheckDetector.findKing leve IllegalStateException des la recursion suivante).
     */
    static final int KING_CAPTURE_SCORE = MATE_SCORE + 10_000;

    public static boolean isMateScore(int score) {
        return Math.abs(score) >= MATE_THRESHOLD;
    }

    /**
     * Un coup candidat a la racine et son score negamax, du point de vue du joueur qui le
     * joue (positif = bon pour lui). Analogue du Candidate de StockfishChessEngine.
     */
    public record ScoredMove(Move move, int score) {
    }

    /**
     * @return tous les coups legaux de board, chacun avec son score negamax a la
     * profondeur donnee, tries du meilleur au moins bon (du point de vue du joueur au
     * trait). depth >= 1 (profondeur 0 n'aurait aucun coup a comparer, utiliser
     * PositionEvaluator.evaluate directement dans ce cas).
     */
    public static List<ScoredMove> searchRoot(Board board, int depth) {
        return searchRoot(board, depth, new TranspositionTable());
    }

    /**
     * Comme searchRoot(Board, int), mais partage tt (etape 24, table de transposition)
     * entre tous les coups racine explores - et, si l'appelant la reutilise entre plusieurs
     * appels (ex. NegamaxTimedAgent entre deux profondeurs d'un approfondissement iteratif,
     * GuessAwareSearch entre ses delegations classiques), entre plusieurs recherches
     * successives. Ne change jamais une valeur retournee (voir TranspositionTable) - tt
     * n'est utilisee que pour eviter de recalculer une valeur deja prouvee, jamais pour
     * reordonner les coups : searchRoot(Board, int) reste donc un cas particulier strictement
     * equivalent, seulement plus lent (table jetable a chaque appel).
     */
    public static List<ScoredMove> searchRoot(Board board, int depth, TranspositionTable tt) {
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be >= 1, got " + depth);
        }
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        if (legalMoves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to search from");
        }
        SearchHeuristics heuristics = new SearchHeuristics();
        List<ScoredMove> results = new ArrayList<>(legalMoves.size());
        for (Move move : orderMoves(legalMoves)) {
            int score = scoreMove(board, move, depth - 1, -INFINITY, INFINITY, tt, heuristics);
            results.add(new ScoredMove(move, score));
        }
        results.sort(Comparator.comparingInt(ScoredMove::score).reversed());
        return results;
    }

    /**
     * @return le score de board du point de vue du joueur au trait (board.sideToMove()) -
     * convention negamax standard, chaque niveau de recursion negue le score renvoye par
     * l'appel suivant. tt (etape 24) cache les valeurs deja prouvees par hash Zobrist +
     * profondeur restante : un hit renvoie directement le score sans jamais reexplorer ni
     * changer l'ordre des coups (probe/store de TranspositionTable), equivalent en valeur a
     * un alpha-beta sans table, seulement plus rapide en presence de transpositions - dont
     * la mecanique de devinette produit beaucoup (Board.pass(pass(x)) == x, voir CLAUDE.md).
     * heuristics (etape 25, tri killer/historique + PVS) n'agit que sur les coups FRERES
     * d'un meme appel - jamais sur l'enumeration des coups racine (voir searchRoot, qui
     * garde son propre orderMoves(List) MVV-LVA d'origine) : aucun impact sur la valeur
     * retournee, seulement sur l'efficacite de l'elagage a l'interieur d'un coup racine deja
     * fixe.
     */
    static int negamax(Board board, int depth, int alpha, int beta, TranspositionTable tt, SearchHeuristics heuristics) {
        long hash = ZobristHash.hash(board);
        Integer cached = tt.probe(hash, depth, alpha, beta);
        if (cached != null) {
            return cached;
        }
        Color color = board.sideToMove();
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, color);
        if (legalMoves.isEmpty()) {
            // Mat : tres negatif pour le joueur au trait (il vient d'etre mate). +depth
            // (plis encore disponibles) pour preferer un mat trouve plus tot dans l'arbre.
            int value = CheckDetector.isInCheck(board, color) ? -(MATE_SCORE + depth) : 0; // pat
            tt.store(hash, depth, value, TranspositionTable.Bound.EXACT);
            return value;
        }
        if (depth == 0) {
            int value = perspectiveEval(board);
            tt.store(hash, depth, value, TranspositionTable.Bound.EXACT);
            return value;
        }
        int originalAlpha = alpha;
        int best = -INFINITY;
        boolean first = true;
        for (Move move : orderMoves(legalMoves, heuristics, depth)) {
            int score;
            if (first) {
                score = scoreMove(board, move, depth - 1, -beta, -alpha, tt, heuristics);
                first = false;
            } else {
                // PVS : fenetre nulle d'abord (ce coup n'est suppose pas meilleur que alpha
                // deja trouve) ; re-recherche en fenetre complete seulement si le resultat
                // est ambigu (alpha < score < beta) - protocole standard, ne change jamais
                // la valeur finale, seulement le nombre de noeuds explores.
                score = scoreMove(board, move, depth - 1, -alpha - 1, -alpha, tt, heuristics);
                if (score > alpha && score < beta) {
                    score = scoreMove(board, move, depth - 1, -beta, -alpha, tt, heuristics);
                }
            }
            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                if (!move.isCapture() && move.type() != MoveType.PROMOTION) {
                    heuristics.recordCutoff(move, depth);
                }
                break; // elagage
            }
        }
        tt.store(hash, depth, best, boundFor(best, originalAlpha, beta));
        return best;
    }

    /** Classification standard alpha-beta+TT : cutoff = seulement une borne inf/sup prouvee, sinon exacte. */
    private static TranspositionTable.Bound boundFor(int value, int alpha, int beta) {
        if (value <= alpha) {
            return TranspositionTable.Bound.UPPERBOUND;
        }
        if (value >= beta) {
            return TranspositionTable.Bound.LOWERBOUND;
        }
        return TranspositionTable.Bound.EXACT;
    }

    /**
     * Score d'un coup depuis son plateau parent - negamax classique, sauf s'il capture un
     * roi (voir KING_CAPTURE_SCORE), auquel cas on renvoie directement une victoire
     * certaine sans jamais construire ni recurser dans le plateau sans roi qui en
     * resulterait.
     */
    private static int scoreMove(Board board, Move move, int depth, int alpha, int beta, TranspositionTable tt, SearchHeuristics heuristics) {
        if (move.isCapture() && move.capturedPiece().type() == PieceType.KING) {
            return KING_CAPTURE_SCORE;
        }
        return -negamax(board.applyMove(move), depth, alpha, beta, tt, heuristics);
    }

    /** Depassement du budget de temps (voir searchRootWithDeadline) - sans pile d'appels, il sert de signal. */
    public static final class SearchTimeoutException extends RuntimeException {
        SearchTimeoutException() {
            super("negamax search timed out", null, false, false);
        }
    }

    /**
     * Comme searchRoot, mais abandonne (SearchTimeoutException) des que deadlineNanos
     * (System.nanoTime()) est depasse, verifie a CHAQUE noeud de la recursion - contrairement
     * a searchRoot/negamax (inchangees, utilisees par minimax@1 fige), necessaire pour qu'un
     * budget de temps reste un budget : sans verification a l'interieur de la recursion, une
     * seule profondeur peut a elle seule depasser tres largement le budget (croissance
     * exponentielle du nombre de noeuds par profondeur). Utilisee par NegamaxTimedAgent
     * (etape 22, tournoi) en approfondissement iteratif - l'appelant est responsable de
     * garder le dernier resultat complet en cas de timeout.
     */
    public static List<ScoredMove> searchRootWithDeadline(Board board, int depth, long deadlineNanos) {
        return searchRootWithDeadline(board, depth, deadlineNanos, new TranspositionTable());
    }

    /**
     * Comme searchRootWithDeadline(Board, int, long), mais partage tt (etape 24) entre
     * plusieurs appels - NegamaxTimedAgent en cree une seule par decision et la reutilise a
     * chaque profondeur de son approfondissement iteratif : les profondeurs courtes deja
     * calculees accelerent les profondeures suivantes des qu'une transposition est retrouvee.
     */
    public static List<ScoredMove> searchRootWithDeadline(Board board, int depth, long deadlineNanos, TranspositionTable tt) {
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be >= 1, got " + depth);
        }
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        if (legalMoves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to search from");
        }
        SearchHeuristics heuristics = new SearchHeuristics();
        List<ScoredMove> results = new ArrayList<>(legalMoves.size());
        for (Move move : orderMoves(legalMoves)) {
            int score = scoreMoveWithDeadline(board, move, depth - 1, -INFINITY, INFINITY, deadlineNanos, tt, heuristics);
            results.add(new ScoredMove(move, score));
        }
        results.sort(Comparator.comparingInt(ScoredMove::score).reversed());
        return results;
    }

    private static int negamaxWithDeadline(Board board, int depth, int alpha, int beta, long deadlineNanos, TranspositionTable tt, SearchHeuristics heuristics) {
        if (System.nanoTime() > deadlineNanos) {
            throw new SearchTimeoutException();
        }
        long hash = ZobristHash.hash(board);
        Integer cached = tt.probe(hash, depth, alpha, beta);
        if (cached != null) {
            return cached;
        }
        Color color = board.sideToMove();
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, color);
        if (legalMoves.isEmpty()) {
            int value = CheckDetector.isInCheck(board, color) ? -(MATE_SCORE + depth) : 0;
            tt.store(hash, depth, value, TranspositionTable.Bound.EXACT);
            return value;
        }
        if (depth == 0) {
            int value = perspectiveEval(board);
            tt.store(hash, depth, value, TranspositionTable.Bound.EXACT);
            return value;
        }
        int originalAlpha = alpha;
        int best = -INFINITY;
        boolean first = true;
        for (Move move : orderMoves(legalMoves, heuristics, depth)) {
            int score;
            if (first) {
                score = scoreMoveWithDeadline(board, move, depth - 1, -beta, -alpha, deadlineNanos, tt, heuristics);
                first = false;
            } else {
                score = scoreMoveWithDeadline(board, move, depth - 1, -alpha - 1, -alpha, deadlineNanos, tt, heuristics);
                if (score > alpha && score < beta) {
                    score = scoreMoveWithDeadline(board, move, depth - 1, -beta, -alpha, deadlineNanos, tt, heuristics);
                }
            }
            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                if (!move.isCapture() && move.type() != MoveType.PROMOTION) {
                    heuristics.recordCutoff(move, depth);
                }
                break;
            }
        }
        tt.store(hash, depth, best, boundFor(best, originalAlpha, beta));
        return best;
    }

    private static int scoreMoveWithDeadline(Board board, Move move, int depth, int alpha, int beta, long deadlineNanos, TranspositionTable tt, SearchHeuristics heuristics) {
        if (move.isCapture() && move.capturedPiece().type() == PieceType.KING) {
            return KING_CAPTURE_SCORE;
        }
        return -negamaxWithDeadline(board.applyMove(move), depth, alpha, beta, deadlineNanos, tt, heuristics);
    }

    static int perspectiveEval(Board board) {
        int whiteEval = PositionEvaluator.evaluate(board);
        return board.sideToMove() == Color.WHITE ? whiteEval : -whiteEval;
    }

    /**
     * Tri des coups avant exploration (captures et promotions d'abord, MVV-LVA sommaire)
     * - ne change aucun resultat (chaque coup racine reste explore, chaque branche reste
     * elaguee selon les memes regles), seulement l'efficacite de l'elagage alpha-beta :
     * une bonne capture trouvee tot resserre alpha/beta plus vite pour les freres suivants.
     */
    static List<Move> orderMoves(List<Move> moves) {
        return moves.stream()
                .sorted(Comparator.comparingInt((Move move) -> orderingScore(move)).reversed())
                .toList();
    }

    /**
     * Comme orderMoves(List), plus un bonus killer/historique (etape 25) sur les coups
     * tranquilles (score MVV-LVA nul, jamais les captures/promotions) - reservee a la boucle
     * interne de negamax/negamaxWithDeadline, jamais a l'enumeration des coups racine (voir
     * la javadoc de la classe SearchHeuristics).
     */
    private static List<Move> orderMoves(List<Move> moves, SearchHeuristics heuristics, int depth) {
        return moves.stream()
                .sorted(Comparator.comparingInt((Move move) -> orderingScore(move, heuristics, depth)).reversed())
                .toList();
    }

    private static int orderingScore(Move move) {
        int score = 0;
        if (move.isCapture()) {
            score += 10_000
                    + PositionEvaluator.pieceValue(move.capturedPiece().type()) * 10
                    - PositionEvaluator.pieceValue(move.movedPiece().type());
        }
        if (move.type() == MoveType.PROMOTION) {
            score += 5_000 + PositionEvaluator.pieceValue(move.promotionType());
        }
        return score;
    }

    private static int orderingScore(Move move, SearchHeuristics heuristics, int depth) {
        int score = orderingScore(move);
        if (score == 0) {
            if (heuristics.isKiller(move, depth)) {
                score += 9_000;
            }
            score += heuristics.historyScore(move);
        }
        return score;
    }
}
