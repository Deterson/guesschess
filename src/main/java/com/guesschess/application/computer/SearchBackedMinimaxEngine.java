package com.guesschess.application.computer;

import com.guesschess.application.computer.NegamaxSearch.ScoredMove;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.MoveGenerator;

import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Implementation partagee de ChessEngine pour minimax@2 (etape 25, +quiescence) et minimax@3
 * (etape 27, +quiescence +eval enrichie) - reprend a l'identique l'orchestration de
 * MinimaxChessEngine (minimax@1, gele - jamais touchee ni reutilisee directement par cette
 * classe pour eliminer tout risque sur Minimax1GoldenTest), parametree par rootSearch, seule
 * chose qui differe entre les deux nouvelles versions (voir BuiltInAgents : minimax@2 passe
 * NegamaxSearch.searchRootQuiescent avec PositionEvaluator::evaluate, minimax@3 le meme avec
 * EnrichedPositionEvaluator::evaluate) - le seul endroit qui choisit entre les deux, jamais un
 * "if" ici, conformement a la regle de versionnage du moteur (voir CLAUDE.md).
 */
final class SearchBackedMinimaxEngine implements ChessEngine {

    /** (board, depth, table de transposition partagee) -> coups racine tries, meilleur d'abord. */
    @FunctionalInterface
    interface RootSearch {
        List<ScoredMove> search(Board board, int depth, TranspositionTable tt);
    }

    private final RootSearch rootSearch;
    private final RandomGenerator rng;

    SearchBackedMinimaxEngine(RootSearch rootSearch, RandomGenerator rng) {
        this.rootSearch = rootSearch;
        this.rng = rng;
    }

    /** Meme levier de profondeur par niveau que MinimaxChessEngine (etape 17). */
    private record EngineSettings(int depth) {
        static EngineSettings forLevel(ComputerLevel level) {
            return switch (level) {
                case EASY -> new EngineSettings(2);
                case MEDIUM -> new EngineSettings(3);
                case HARD -> new EngineSettings(4);
            };
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Move chooseMove(Board board, List<Move> legalMoves, ComputerLevel level, Set<Move> movesToAvoidIfPossible) {
        if (legalMoves.isEmpty()) {
            throw new IllegalArgumentException("no legal move to choose from");
        }
        int depth = EngineSettings.forLevel(level).depth();
        List<ScoredMove> scored = searchRootIteratively(board, depth);
        List<ScoredMove> withoutAvoidableKingCapture = excludeKingCaptureUnlessForced(scored);
        List<ScoredMove> preferred = excludeIfAlternativeExists(withoutAvoidableKingCapture, movesToAvoidIfPossible);
        Move chosen = switch (level) {
            case EASY -> pickByRankWeight(preferred);
            case MEDIUM -> preferred.get(0).move();
            case HARD -> pickPreferringGuessableRefutation(board, preferred);
        };
        return resolveAgainstLegalMoves(chosen, legalMoves);
    }

    /** Approfondissement iteratif jusqu'a depth, meme pattern que MinimaxChessEngine (etape 25). */
    private List<ScoredMove> searchRootIteratively(Board board, int depth) {
        TranspositionTable tt = new TranspositionTable();
        List<ScoredMove> result = rootSearch.search(board, 1, tt);
        for (int d = 2; d <= depth; d++) {
            result = rootSearch.search(board, d, tt);
        }
        return result;
    }

    private static List<ScoredMove> excludeKingCaptureUnlessForced(List<ScoredMove> scored) {
        List<ScoredMove> withoutKingCapture = scored.stream()
                .filter(sm -> !isKingCapture(sm.move()))
                .toList();
        return withoutKingCapture.isEmpty() ? scored : withoutKingCapture;
    }

    private static boolean isKingCapture(Move move) {
        return move.isCapture() && move.capturedPiece().type() == PieceType.KING;
    }

    private static final int GAP_THRESHOLD_CP = 150;
    private static final int REFUTATION_CHECK_DEPTH = 2;

    private Move pickPreferringGuessableRefutation(Board board, List<ScoredMove> candidates) {
        ScoredMove classicalBest = candidates.get(0);
        List<ScoredMove> nearBest = candidates.stream()
                .filter(c -> classicalBest.score() - c.score() <= GAP_THRESHOLD_CP)
                .toList();
        for (ScoredMove candidate : nearBest) {
            if (exposesGuessableRefutation(board, candidate.move())) {
                return candidate.move();
            }
        }
        return classicalBest.move();
    }

    private boolean exposesGuessableRefutation(Board board, Move move) {
        Board afterMove = board.applyMove(move);
        Color opponent = afterMove.sideToMove();
        if (!MoveGenerator.hasAnyLegalMove(afterMove, opponent)) {
            return false;
        }
        List<ScoredMove> replies = rootSearch.search(afterMove, REFUTATION_CHECK_DEPTH, new TranspositionTable());
        if (replies.size() < 2) {
            return false;
        }
        ScoredMove best = replies.get(0);
        if (NegamaxSearch.isMateScore(best.score())) {
            return false;
        }
        ScoredMove second = replies.get(1);
        return best.score() - second.score() > GAP_THRESHOLD_CP;
    }

    private static List<ScoredMove> excludeIfAlternativeExists(List<ScoredMove> scored, Set<Move> movesToAvoidIfPossible) {
        if (movesToAvoidIfPossible.isEmpty()) {
            return scored;
        }
        List<ScoredMove> filtered = scored.stream()
                .filter(sm -> !movesToAvoidIfPossible.contains(sm.move()))
                .toList();
        return filtered.isEmpty() ? scored : filtered;
    }

    private Move pickByRankWeight(List<ScoredMove> scored) {
        if (scored.size() == 1) {
            return scored.get(0).move();
        }
        double[] weights = {0.5, 0.3, 0.2};
        double roll = rng.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < scored.size(); i++) {
            cumulative += i < weights.length ? weights[i] : 0;
            if (roll < cumulative) {
                return scored.get(i).move();
            }
        }
        return scored.get(0).move();
    }

    private static Move resolveAgainstLegalMoves(Move candidate, List<Move> legalMoves) {
        return legalMoves.stream().filter(candidate::equals).findFirst().orElse(candidate);
    }
}
