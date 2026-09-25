package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation enrichie (etape 27 de la roadmap), en centipawns, du point de vue des blancs -
 * moteur de minimax@3 (voir SearchBackedMinimaxEngine/BuiltInAgents) ; jamais utilisee par
 * minimax@1/minimax@2, qui restent sur PositionEvaluator (inchange, gele avec eux). Reprend
 * PositionEvaluator (materiel, tables positionnelles, mobilite) tel quel plutot que de le
 * dupliquer - une seule correction additive (voir taperedKingCorrectionForSide) puis les
 * briques listees a l'etape 27 par-dessus : structure de pions (doubles/isoles/passes),
 * securite du roi (bouclier de pions), paire de fous, tour sur colonne ouverte/semi-ouverte,
 * et un eval "tapered" entre milieu de partie et finale.
 *
 * Tapered eval volontairement limite a la table du roi (celle de PositionEvaluator, pensee
 * pour le roque, interpolee avec KING_ENDGAME_PST, pensee pour la centralisation) plutot que
 * de retabler aussi pions/pieces mineures/tour/dame : c'est la ou le gain pratique de la
 * technique est le plus net (un roi qui reste au roque en finale perd du temps), le reste
 * aurait multiplie le cout par noeud (deja double par rapport a PositionEvaluator seul, voir
 * engines.md) pour un gain incertain - a mesurer au tournoi (etape 22) avant d'aller plus loin.
 */
public final class EnrichedPositionEvaluator {

    private EnrichedPositionEvaluator() {
    }

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    /**
     * Poids de phase standard ("Tapered Eval", programmation d'echecs) : total 24 a la
     * position de depart (2 cavaliers + 2 fous + 2 tours*2 + 1 dame*4 = 12 par camp), decroit
     * vers 0 a mesure que les pieces sont echangees - 0 = finale, 24 = milieu/ouverture.
     */
    private static final int PHASE_KNIGHT = 1;
    private static final int PHASE_BISHOP = 1;
    private static final int PHASE_ROOK = 2;
    private static final int PHASE_QUEEN = 4;
    private static final int TOTAL_PHASE = 24;

    /** Roi en finale : encourage a se centraliser plutot qu'a rester au roque (voir PositionEvaluator.KING_PST, milieu de partie). */
    private static final int[][] KING_ENDGAME_PST = {
            {-50, -40, -30, -20, -20, -30, -40, -50},
            {-30, -20, -10, 0, 0, -10, -20, -30},
            {-30, -10, 20, 30, 30, 20, -10, -30},
            {-30, -10, 30, 40, 40, 30, -10, -30},
            {-30, -10, 30, 40, 40, 30, -10, -30},
            {-30, -10, 20, 30, 30, 20, -10, -30},
            {-30, -30, 0, 0, 0, 0, -30, -30},
            {-50, -30, -30, -30, -30, -30, -30, -50},
    };

    private static final int DOUBLED_PAWN_PENALTY = 12;
    private static final int ISOLATED_PAWN_PENALTY = 15;

    /** Indexe par nombre de rangees deja parcourues depuis la case de depart (0..6). */
    private static final int[] PASSED_PAWN_BONUS_BY_ADVANCEMENT = {0, 5, 10, 20, 35, 60, 100};

    private static final int BISHOP_PAIR_BONUS = 30;
    private static final int ROOK_OPEN_FILE_BONUS = 20;
    private static final int ROOK_SEMI_OPEN_FILE_BONUS = 10;

    /** Par colonne (parmi les 3 propres au roi) sans pion ami - voir missingShieldFiles. */
    private static final int KING_SHIELD_FILE_PENALTY = 12;

    public static int evaluate(Board board) {
        int[][] pawnFiles = new int[2][8];
        List<Position> whitePawns = new ArrayList<>();
        List<Position> blackPawns = new ArrayList<>();
        List<Position> whiteRooks = new ArrayList<>();
        List<Position> blackRooks = new ArrayList<>();
        Position whiteKing = null;
        Position blackKing = null;
        int whiteBishops = 0;
        int blackBishops = 0;
        int phase = 0;

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Position pos = Position.of(file, rank);
                Piece piece = board.pieceAt(pos);
                if (piece == null) {
                    continue;
                }
                int colorIdx = piece.color() == Color.WHITE ? WHITE : BLACK;
                switch (piece.type()) {
                    case PAWN -> {
                        pawnFiles[colorIdx][file]++;
                        (colorIdx == WHITE ? whitePawns : blackPawns).add(pos);
                    }
                    case KNIGHT -> phase += PHASE_KNIGHT;
                    case BISHOP -> {
                        if (colorIdx == WHITE) {
                            whiteBishops++;
                        } else {
                            blackBishops++;
                        }
                        phase += PHASE_BISHOP;
                    }
                    case ROOK -> {
                        (colorIdx == WHITE ? whiteRooks : blackRooks).add(pos);
                        phase += PHASE_ROOK;
                    }
                    case QUEEN -> phase += PHASE_QUEEN;
                    case KING -> {
                        if (colorIdx == WHITE) {
                            whiteKing = pos;
                        } else {
                            blackKing = pos;
                        }
                    }
                }
            }
        }
        phase = Math.min(phase, TOTAL_PHASE);

        int score = PositionEvaluator.evaluate(board);
        score += taperedKingCorrectionForSide(whiteKing, Color.WHITE, phase);
        score += taperedKingCorrectionForSide(blackKing, Color.BLACK, phase);
        score += pawnStructureScore(pawnFiles, whitePawns, blackPawns);
        score += kingSafetyScore(pawnFiles, whiteKing, blackKing, phase);
        score += bishopPairScore(whiteBishops, blackBishops);
        score += rookFileScore(pawnFiles, whiteRooks, blackRooks);
        return score;
    }

    /**
     * PositionEvaluator.evaluate() a deja ajoute +-kingMidgamePositionalValue(king) - cette
     * methode renvoie seulement la correction (tapered - milieu de partie) a ajouter par-dessus,
     * jamais la valeur du roi elle-meme (deja comptee).
     */
    private static int taperedKingCorrectionForSide(Position king, Color color, int phase) {
        int midgame = PositionEvaluator.kingMidgamePositionalValue(king.file(), king.rank(), color);
        int endgame = kingEndgamePositionalValue(king.file(), king.rank(), color);
        int tapered = (midgame * phase + endgame * (TOTAL_PHASE - phase)) / TOTAL_PHASE;
        int delta = tapered - midgame;
        return color == Color.WHITE ? delta : -delta;
    }

    private static int kingEndgamePositionalValue(int file, int rank, Color color) {
        int row = color == Color.WHITE ? 7 - rank : rank;
        return KING_ENDGAME_PST[row][file];
    }

    private static int pawnStructureScore(int[][] pawnFiles, List<Position> whitePawns, List<Position> blackPawns) {
        int white = 0;
        int black = 0;
        for (int file = 0; file < 8; file++) {
            if (pawnFiles[WHITE][file] > 1) {
                white -= DOUBLED_PAWN_PENALTY * (pawnFiles[WHITE][file] - 1);
            }
            if (pawnFiles[BLACK][file] > 1) {
                black -= DOUBLED_PAWN_PENALTY * (pawnFiles[BLACK][file] - 1);
            }
            if (pawnFiles[WHITE][file] > 0 && isIsolated(pawnFiles, WHITE, file)) {
                white -= ISOLATED_PAWN_PENALTY * pawnFiles[WHITE][file];
            }
            if (pawnFiles[BLACK][file] > 0 && isIsolated(pawnFiles, BLACK, file)) {
                black -= ISOLATED_PAWN_PENALTY * pawnFiles[BLACK][file];
            }
        }
        for (Position pawn : whitePawns) {
            if (isPassed(pawn, Color.WHITE, blackPawns)) {
                white += passedPawnBonus(pawn, Color.WHITE);
            }
        }
        for (Position pawn : blackPawns) {
            if (isPassed(pawn, Color.BLACK, whitePawns)) {
                black += passedPawnBonus(pawn, Color.BLACK);
            }
        }
        return white - black;
    }

    private static boolean isIsolated(int[][] pawnFiles, int colorIdx, int file) {
        boolean left = file > 0 && pawnFiles[colorIdx][file - 1] > 0;
        boolean right = file < 7 && pawnFiles[colorIdx][file + 1] > 0;
        return !left && !right;
    }

    /** Aucun pion adverse ne le bloque ou ne peut le capturer sur sa colonne ou les colonnes voisines, en avancant. */
    private static boolean isPassed(Position pawn, Color color, List<Position> enemyPawns) {
        for (Position enemy : enemyPawns) {
            if (Math.abs(enemy.file() - pawn.file()) > 1) {
                continue;
            }
            boolean ahead = color == Color.WHITE ? enemy.rank() > pawn.rank() : enemy.rank() < pawn.rank();
            if (ahead) {
                return false;
            }
        }
        return true;
    }

    private static int passedPawnBonus(Position pawn, Color color) {
        int advancement = color == Color.WHITE ? pawn.rank() - 1 : 6 - pawn.rank();
        advancement = Math.max(0, Math.min(advancement, PASSED_PAWN_BONUS_BY_ADVANCEMENT.length - 1));
        return PASSED_PAWN_BONUS_BY_ADVANCEMENT[advancement];
    }

    /** Poids par phase (etape 27) : la securite du roi importe surtout hors finale, jamais retiree completement pour autant. */
    private static int kingSafetyScore(int[][] pawnFiles, Position whiteKing, Position blackKing, int phase) {
        int whiteExposure = missingShieldFiles(pawnFiles, WHITE, whiteKing.file());
        int blackExposure = missingShieldFiles(pawnFiles, BLACK, blackKing.file());
        int raw = (blackExposure - whiteExposure) * KING_SHIELD_FILE_PENALTY;
        return raw * phase / TOTAL_PHASE;
    }

    /** Nombre de colonnes, parmi celle du roi et ses deux voisines, sans aucun pion ami (bouclier absent). */
    private static int missingShieldFiles(int[][] pawnFiles, int colorIdx, int kingFile) {
        int missing = 0;
        for (int file = Math.max(0, kingFile - 1); file <= Math.min(7, kingFile + 1); file++) {
            if (pawnFiles[colorIdx][file] == 0) {
                missing++;
            }
        }
        return missing;
    }

    private static int bishopPairScore(int whiteBishops, int blackBishops) {
        int score = 0;
        if (whiteBishops >= 2) {
            score += BISHOP_PAIR_BONUS;
        }
        if (blackBishops >= 2) {
            score -= BISHOP_PAIR_BONUS;
        }
        return score;
    }

    private static int rookFileScore(int[][] pawnFiles, List<Position> whiteRooks, List<Position> blackRooks) {
        int score = 0;
        for (Position rook : whiteRooks) {
            score += rookFileBonus(pawnFiles, WHITE, rook.file());
        }
        for (Position rook : blackRooks) {
            score -= rookFileBonus(pawnFiles, BLACK, rook.file());
        }
        return score;
    }

    private static int rookFileBonus(int[][] pawnFiles, int colorIdx, int file) {
        int opponentIdx = colorIdx == WHITE ? BLACK : WHITE;
        boolean ownPawnOnFile = pawnFiles[colorIdx][file] > 0;
        boolean enemyPawnOnFile = pawnFiles[opponentIdx][file] > 0;
        if (!ownPawnOnFile && !enemyPawnOnFile) {
            return ROOK_OPEN_FILE_BONUS;
        }
        if (!ownPawnOnFile) {
            return ROOK_SEMI_OPEN_FILE_BONUS;
        }
        return 0;
    }
}
