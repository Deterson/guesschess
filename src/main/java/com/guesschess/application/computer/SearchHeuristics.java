package com.guesschess.application.computer;

import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;

/**
 * Heuristiques de tri des coups INTERNES a la recursion de negamax (etape 25) - jamais
 * utilisees pour l'enumeration des coups racine (searchRoot/searchRootWithDeadline gardent
 * leur propre orderMoves(List) d'origine, MVV-LVA seul) : elles n'influencent donc jamais
 * quel coup racine est premier parmi des scores egaux, seulement l'efficacite de l'elagage
 * a l'interieur d'un coup racine deja fixe - aucun changement de valeur retournee, comme la
 * TranspositionTable de l'etape 24.
 *
 * - Killer moves : jusqu'a 2 coups tranquilles (ni capture ni promotion) par profondeur
 *   restante ayant deja cause une coupe beta a un coup FRERE. La profondeur restante est
 *   une distance exacte a la racine pour un appel de recherche donne (elle decroit de 1 a
 *   chaque niveau de recursion, comme dans NegamaxSearch), pas besoin de suivre un ply
 *   absolu separe.
 * - Historique : score cumule par (case depart, case arrivee) des coups tranquilles ayant
 *   cause une coupe, pondere par depth^2 - convention standard ("history heuristic"), plus
 *   une coupe profonde compte, plus le coup est considere fiable.
 *
 * Une instance par appel racine (searchRoot/searchRootWithDeadline), jamais partagee entre
 * agents/parties differentes - meme raisonnement de simplicite que TranspositionTable
 * (aucun etat a partager entre profondeurs d'un approfondissement iteratif, son benefice
 * est intra-arbre, entre coups freres d'une meme recherche).
 */
final class SearchHeuristics {

    private static final int MAX_TRACKED_DEPTH = 64;

    private final Move[][] killers = new Move[MAX_TRACKED_DEPTH][2];
    private final int[][] history = new int[64][64];

    boolean isKiller(Move move, int depth) {
        Move[] slot = killers[index(depth)];
        return move.equals(slot[0]) || move.equals(slot[1]);
    }

    int historyScore(Move move) {
        return history[squareIndex(move.from())][squareIndex(move.to())];
    }

    /** A appeler uniquement pour un coup tranquille (ni capture ni promotion) qui vient de causer une coupe beta. */
    void recordCutoff(Move move, int depth) {
        Move[] slot = killers[index(depth)];
        if (!move.equals(slot[0])) {
            slot[1] = slot[0];
            slot[0] = move;
        }
        history[squareIndex(move.from())][squareIndex(move.to())] += depth * depth;
    }

    private static int index(int depth) {
        return Math.max(0, Math.min(depth, MAX_TRACKED_DEPTH - 1));
    }

    private static int squareIndex(Position position) {
        return position.rank() * 8 + position.file();
    }
}
