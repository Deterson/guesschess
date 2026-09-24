package com.guesschess.tournament;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.rules.MoveGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Positions de depart variees pour le tournoi (etape 22) : quelques demi-coups aleatoires
 * depuis la position initiale, graine fixe -> reproductible. Chaque position est jouee des
 * deux cotes (voir RoundRobinScheduler) pour equilibrer la couleur, donc le desequilibre
 * introduit par un nombre impair de demi-coups aleatoires n'avantage durablement personne.
 */
public final class OpeningPositionGenerator {

    private OpeningPositionGenerator() {
    }

    public static List<Board> generate(int count, int plies, long seed) {
        List<Board> openings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long openingSeed = seed ^ (0x9E3779B97F4A7C15L * (i + 1));
            openings.add(randomWalk(plies, new Random(openingSeed)));
        }
        return openings;
    }

    private static Board randomWalk(int plies, Random rng) {
        Board board = Board.initial();
        for (int ply = 0; ply < plies; ply++) {
            List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
            if (legalMoves.isEmpty()) {
                break;
            }
            board = board.applyMove(legalMoves.get(rng.nextInt(legalMoves.size())));
        }
        return board;
    }
}
