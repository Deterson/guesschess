package com.guesschess.application.computer;

import com.guesschess.application.computer.NegamaxSearch.ScoredMove;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimaxChessEngineTest {

    private final MinimaxChessEngine engine = new MinimaxChessEngine();

    @Test
    void isAlwaysAvailable() {
        assertTrue(engine.isAvailable());
    }

    @Test
    void mediumAndHardAlwaysReturnTheBestScoredMove() {
        Board board = freeRookCapturePosition();
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());

        Move medium = engine.chooseMove(board, legalMoves, ComputerLevel.MEDIUM, Set.of());
        Move hard = engine.chooseMove(board, legalMoves, ComputerLevel.HARD, Set.of());

        assertEquals(Position.fromAlgebraic("d1"), medium.from());
        assertEquals(Position.fromAlgebraic("d8"), medium.to());
        assertEquals(medium, hard);
    }

    @Test
    void avoidsAMoveWhenAnAlternativeExists() {
        Board board = freeRookCapturePosition();
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        Move bestCapture = engine.chooseMove(board, legalMoves, ComputerLevel.HARD, Set.of());

        Move chosen = engine.chooseMove(board, legalMoves, ComputerLevel.HARD, Set.of(bestCapture));

        assertTrue(!chosen.equals(bestCapture), "should have avoided " + bestCapture);
    }

    @Test
    void neverEmptiesTheCandidateListWhenEveryMoveIsToAvoid() {
        // Roi seul, un unique coup legal (Ka1-a2, ou toute autre case libre) : aucune
        // alternative possible, l'exclusion doit etre ignoree plutot que de planter.
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        Set<Move> allMoves = Set.copyOf(legalMoves);

        Move chosen = engine.chooseMove(board, legalMoves, ComputerLevel.HARD, allMoves);

        assertTrue(legalMoves.contains(chosen));
    }

    @Test
    void easyOnlyEverPicksAmongTheTopThreeScoredMoves() {
        Board board = Board.initial();
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        List<ScoredMove> reference = NegamaxSearch.searchRoot(board, 2);
        Set<Move> topThree = reference.stream().limit(3).map(ScoredMove::move).collect(Collectors.toSet());

        for (int i = 0; i < 30; i++) {
            Move chosen = engine.chooseMove(board, legalMoves, ComputerLevel.EASY, Set.of());
            assertTrue(topThree.contains(chosen), "easy picked outside the top 3: " + chosen);
        }
    }

    /**
     * Deux coups blancs legaux, de score classique tres proche (a l'interieur de
     * GAP_THRESHOLD_CP) : deplacer la dame en securite (h5, aucune reponse noire
     * evidente derriere) ou pousser le roi (e1-e2, qui laisse la dame en prise sur d5 -
     * Txd5 domine alors tres largement toute autre reponse noire). La strategie 1 doit
     * preferer le second malgre son score classique legerement inferieur : le pire cas
     * reel (Txd5 devine puis annule) vaut mieux que ce qu'un minimax classique suppose.
     */
    @Test
    void hardPrefersACandidateThatExposesAGuessableRefutationAmongNearlyEqualMoves() {
        Board board = queenEitherHangsOrRetreatsPosition();
        Move kingShuffleLeavingQueenHanging = Move.normal(
                Position.fromAlgebraic("e1"), Position.fromAlgebraic("e2"),
                Piece.of(PieceType.KING, Color.WHITE), null);
        Move queenRetreatsToSafety = Move.normal(
                Position.fromAlgebraic("d5"), Position.fromAlgebraic("h5"),
                Piece.of(PieceType.QUEEN, Color.WHITE), null);

        assertTrue(MinimaxChessEngine.exposesGuessableRefutation(board, kingShuffleLeavingQueenHanging));
        assertTrue(!MinimaxChessEngine.exposesGuessableRefutation(board, queenRetreatsToSafety));

        List<ScoredMove> candidates = List.of(
                new ScoredMove(queenRetreatsToSafety, 10),
                new ScoredMove(kingShuffleLeavingQueenHanging, 0));

        Move chosen = MinimaxChessEngine.pickPreferringGuessableRefutation(board, candidates);

        assertEquals(kingShuffleLeavingQueenHanging, chosen);
    }

    /**
     * Etat degenere propre a guesschess (round annule qui laisse un roi en echec non
     * resolu, voir CLAUDE.md/ComputerPlayerService) : les noirs ont Ra8xa1 parmi leurs
     * coups legaux, mais pas seulement - ce n'est donc pas un coup force. Capturer le roi
     * blanc ici n'est jamais "gratuit" (le mover n'est pas lui-meme en echec) : un humain
     * qui connait la regle la devine quasi systematiquement, ce qui l'annule sans rien
     * lui avoir coute - le moteur ne doit donc jamais la choisir tant qu'une alternative
     * existe, malgre son score classique toujours le plus haut possible.
     */
    @Test
    void neverChoosesAnAvoidableKingCapture() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        Move kingCapture = legalMoves.stream()
                .filter(m -> m.isCapture() && m.capturedPiece().type() == PieceType.KING)
                .findFirst().orElseThrow();

        for (ComputerLevel level : ComputerLevel.values()) {
            Move chosen = engine.chooseMove(board, legalMoves, level, Set.of());
            assertTrue(!chosen.equals(kingCapture), level + " should not have captured the king: " + chosen);
        }
    }

    /**
     * Deux rois seuls, adjacents (blanc b2, noir a1) : degenere, jamais atteignable en
     * jeu normal, mais le seul coup legal des noirs est Kxb2 (a2 et b1, les deux autres
     * cases voisines de a1, restent attaquees par le roi blanc lui-meme). Contrairement
     * a neverChoosesAnAvoidableKingCapture, ce coup DOIT etre choisi : aucune alternative
     * n'existe, donc aucune previsibilite a eviter.
     */
    @Test
    void stillChoosesAKingCaptureWhenItIsTheOnlyLegalMove() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("b2"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);
        List<Move> legalMoves = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        assertEquals(1, legalMoves.size());
        Move kingCapture = legalMoves.get(0);
        assertTrue(kingCapture.isCapture() && kingCapture.capturedPiece().type() == PieceType.KING);

        for (ComputerLevel level : ComputerLevel.values()) {
            Move chosen = engine.chooseMove(board, legalMoves, level, Set.of());
            assertEquals(kingCapture, chosen, level + " should have captured the king (forced)");
        }
    }

    /** Dame blanche d5, tour noire d8 pouvant la prendre si elle reste sur la colonne d. */
    private static Board queenEitherHangsOrRetreatsPosition() {
        return Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d5"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a7"), Piece.of(PieceType.PAWN, Color.BLACK));
    }

    /** Dame blanche d1 peut prendre gratuitement une tour noire d8 non defendue. */
    private static Board freeRookCapturePosition() {
        return Board.empty()
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.ROOK, Color.BLACK));
    }
}
