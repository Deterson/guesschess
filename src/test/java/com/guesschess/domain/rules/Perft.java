package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;

/**
 * Compte le nombre de positions atteignables a une profondeur donnee, en explorant
 * uniquement des coups legaux. Methode de reference standard pour valider un
 * generateur de coups d'echecs contre des resultats connus.
 */
final class Perft {

    private Perft() {
    }

    static long count(Board board, Color sideToMove, int depth) {
        if (depth == 0) {
            return 1;
        }
        long nodes = 0;
        for (Move move : MoveGenerator.generateLegalMoves(board, sideToMove)) {
            Board after = board.applyMove(move);
            nodes += count(after, sideToMove.opposite(), depth - 1);
        }
        return nodes;
    }
}
