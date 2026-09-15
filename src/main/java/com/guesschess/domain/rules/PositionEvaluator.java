package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;

/**
 * Evaluation statique d'une position, en centipawns, du point de vue des blancs (positif
 * favorise les blancs) - matiere premiere du moteur maison (etape 17 de la roadmap,
 * application.computer.NegamaxSearch). Ne joue aucun coup, ne regarde jamais la
 * mecanique de devinette (voir CLAUDE.md, "couche guess-aware" ajoutee au-dessus, pas
 * ici) : uniquement du materiel, des tables positionnelles standard et un terme de
 * mobilite leger, comme n'importe quel moteur amateur. Pas d'objectif de force brute -
 * voir la note de conception qui a precede cette classe.
 */
public final class PositionEvaluator {

    private PositionEvaluator() {
    }

    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;

    /**
     * Bonus/malus de mobilite (etape 17) : difference du nombre de coups pseudo-legaux
     * entre les deux couleurs, ponderee legerement. Pseudo-legaux plutot que legaux
     * (MoveGenerator.generateLegalMoves) - deja le cout dominant d'un noeud de recherche
     * si on y ajoutait la simulation de securite du roi par coup candidat, alors que ce
     * terme n'a besoin que d'un ordre de grandeur, pas d'un compte exact.
     */
    private static final int MOBILITY_WEIGHT = 2;

    /**
     * Tables positionnelles simplifiees (valeurs standard de programmation d'echecs,
     * "simplified evaluation function"), en centipawns, ecrites rangee 8 en premier
     * (index 0) jusqu'a rangee 1 (index 7) - c'est la lecture naturelle d'un diagramme
     * d'echecs. positionalValue() se charge de l'indexation miroir pour les noirs.
     */
    private static final int[][] PAWN_PST = {
            {0, 0, 0, 0, 0, 0, 0, 0},
            {50, 50, 50, 50, 50, 50, 50, 50},
            {10, 10, 20, 30, 30, 20, 10, 10},
            {5, 5, 10, 25, 25, 10, 5, 5},
            {0, 0, 0, 20, 20, 0, 0, 0},
            {5, -5, -10, 0, 0, -10, -5, 5},
            {5, 10, 10, -20, -20, 10, 10, 5},
            {0, 0, 0, 0, 0, 0, 0, 0},
    };

    private static final int[][] KNIGHT_PST = {
            {-50, -40, -30, -30, -30, -30, -40, -50},
            {-40, -20, 0, 0, 0, 0, -20, -40},
            {-30, 0, 10, 15, 15, 10, 0, -30},
            {-30, 5, 15, 20, 20, 15, 5, -30},
            {-30, 0, 15, 20, 20, 15, 0, -30},
            {-30, 5, 10, 15, 15, 10, 5, -30},
            {-40, -20, 0, 5, 5, 0, -20, -40},
            {-50, -40, -30, -30, -30, -30, -40, -50},
    };

    private static final int[][] BISHOP_PST = {
            {-20, -10, -10, -10, -10, -10, -10, -20},
            {-10, 0, 0, 0, 0, 0, 0, -10},
            {-10, 0, 5, 10, 10, 5, 0, -10},
            {-10, 5, 5, 10, 10, 5, 5, -10},
            {-10, 0, 10, 10, 10, 10, 0, -10},
            {-10, 10, 10, 10, 10, 10, 10, -10},
            {-10, 5, 0, 0, 0, 0, 5, -10},
            {-20, -10, -10, -10, -10, -10, -10, -20},
    };

    private static final int[][] ROOK_PST = {
            {0, 0, 0, 0, 0, 0, 0, 0},
            {5, 10, 10, 10, 10, 10, 10, 5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {-5, 0, 0, 0, 0, 0, 0, -5},
            {0, 0, 0, 5, 5, 0, 0, 0},
    };

    private static final int[][] QUEEN_PST = {
            {-20, -10, -10, -5, -5, -10, -10, -20},
            {-10, 0, 0, 0, 0, 0, 0, -10},
            {-10, 0, 5, 5, 5, 5, 0, -10},
            {-5, 0, 5, 5, 5, 5, 0, -5},
            {0, 0, 5, 5, 5, 5, 0, -5},
            {-10, 5, 5, 5, 5, 5, 0, -10},
            {-10, 0, 5, 0, 0, 0, 0, -10},
            {-20, -10, -10, -5, -5, -10, -10, -20},
    };

    /** Roi en milieu de partie : encourage a rester derriere ses pions, hors du centre. */
    private static final int[][] KING_PST = {
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-30, -40, -40, -50, -50, -40, -40, -30},
            {-20, -30, -30, -40, -40, -30, -30, -20},
            {-10, -20, -20, -20, -20, -20, -20, -10},
            {20, 20, 0, 0, 0, 0, 20, 20},
            {20, 30, 10, 0, 0, 10, 30, 20},
    };

    public static int pieceValue(PieceType type) {
        return switch (type) {
            case PAWN -> PAWN_VALUE;
            case KNIGHT -> KNIGHT_VALUE;
            case BISHOP -> BISHOP_VALUE;
            case ROOK -> ROOK_VALUE;
            case QUEEN -> QUEEN_VALUE;
            case KING -> 0;
        };
    }

    /**
     * Score en centipawns du point de vue des blancs : positif favorise les blancs,
     * negatif les noirs. Ne distingue pas les positions terminales (mat/pat) - la
     * recherche (NegamaxSearch) s'en charge avant meme d'appeler cette methode.
     */
    public static int evaluate(Board board) {
        int score = 0;
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Piece piece = board.pieceAt(Position.of(file, rank));
                if (piece == null) {
                    continue;
                }
                int value = pieceValue(piece.type()) + positionalValue(piece.type(), file, rank, piece.color());
                score += piece.color() == Color.WHITE ? value : -value;
            }
        }
        int whiteMobility = MoveGenerator.generatePseudoLegalMoves(board, Color.WHITE).size();
        int blackMobility = MoveGenerator.generatePseudoLegalMoves(board, Color.BLACK).size();
        score += (whiteMobility - blackMobility) * MOBILITY_WEIGHT;
        return score;
    }

    /**
     * Lit la table positionnelle du type de piece pour la case (file, rank), rank 0 =
     * rangee 1 (convention Position). Les tables sont ecrites rangee 8 en premier : pour
     * les blancs on lit donc depuis la fin (index 7-rank), pour les noirs directement
     * (index rank) - equivalent a un miroir vertical, la lecture standard pour donner a
     * une piece noire la meme valeur positionnelle relative que son homologue blanche.
     */
    private static int positionalValue(PieceType type, int file, int rank, Color color) {
        int[][] table = tableFor(type);
        int row = color == Color.WHITE ? 7 - rank : rank;
        return table[row][file];
    }

    private static int[][] tableFor(PieceType type) {
        return switch (type) {
            case PAWN -> PAWN_PST;
            case KNIGHT -> KNIGHT_PST;
            case BISHOP -> BISHOP_PST;
            case ROOK -> ROOK_PST;
            case QUEEN -> QUEEN_PST;
            case KING -> KING_PST;
        };
    }
}
