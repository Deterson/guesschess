package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.move.MoveType;
import com.guesschess.domain.piece.Color;
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
        if (depth < 1) {
            throw new IllegalArgumentException("depth must be >= 1, got " + depth);
        }
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        if (legalMoves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to search from");
        }
        List<ScoredMove> results = new ArrayList<>(legalMoves.size());
        for (Move move : orderMoves(legalMoves)) {
            int score = -negamax(board.applyMove(move), depth - 1, -INFINITY, INFINITY);
            results.add(new ScoredMove(move, score));
        }
        results.sort(Comparator.comparingInt(ScoredMove::score).reversed());
        return results;
    }

    /**
     * @return le score de board du point de vue du joueur au trait (board.sideToMove()) -
     * convention negamax standard, chaque niveau de recursion negue le score renvoye par
     * l'appel suivant.
     */
    private static int negamax(Board board, int depth, int alpha, int beta) {
        Color color = board.sideToMove();
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, color);
        if (legalMoves.isEmpty()) {
            if (CheckDetector.isInCheck(board, color)) {
                // Mat : tres negatif pour le joueur au trait (il vient d'etre mate). +depth
                // (plis encore disponibles) pour preferer un mat trouve plus tot dans l'arbre.
                return -(MATE_SCORE + depth);
            }
            return 0; // pat
        }
        if (depth == 0) {
            return perspectiveEval(board);
        }
        int best = -INFINITY;
        for (Move move : orderMoves(legalMoves)) {
            int score = -negamax(board.applyMove(move), depth - 1, -beta, -alpha);
            if (score > best) {
                best = score;
            }
            if (best > alpha) {
                alpha = best;
            }
            if (alpha >= beta) {
                break; // elagage
            }
        }
        return best;
    }

    private static int perspectiveEval(Board board) {
        int whiteEval = PositionEvaluator.evaluate(board);
        return board.sideToMove() == Color.WHITE ? whiteEval : -whiteEval;
    }

    /**
     * Tri des coups avant exploration (captures et promotions d'abord, MVV-LVA sommaire)
     * - ne change aucun resultat (chaque coup racine reste explore, chaque branche reste
     * elaguee selon les memes regles), seulement l'efficacite de l'elagage alpha-beta :
     * une bonne capture trouvee tot resserre alpha/beta plus vite pour les freres suivants.
     */
    private static List<Move> orderMoves(List<Move> moves) {
        return moves.stream()
                .sorted(Comparator.comparingInt(NegamaxSearch::orderingScore).reversed())
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
}
