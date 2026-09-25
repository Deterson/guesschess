package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests des briques ajoutees par EnrichedPositionEvaluator (etape 27) : chaque cas isole une
 * seule brique en gardant le materiel et les autres facteurs identiques entre les deux
 * plateaux compares, pour eviter qu'un effet de bord (materiel, mobilite) ne masque celui
 * teste. Comparaisons directionnelles (comme PositionEvaluatorTest), jamais de valeur exacte -
 * les constantes de ponderation restent un choix ajustable.
 */
class EnrichedPositionEvaluatorTest {

    @Test
    void bishopPairScoresBetterThanBishopAndKnightOfEqualMaterial() {
        Board bishopPair = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("c1"), Piece.of(PieceType.BISHOP, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f1"), Piece.of(PieceType.BISHOP, Color.WHITE));
        Board bishopAndKnight = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("c1"), Piece.of(PieceType.BISHOP, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f1"), Piece.of(PieceType.KNIGHT, Color.WHITE));

        int gap = EnrichedPositionEvaluator.evaluate(bishopPair) - EnrichedPositionEvaluator.evaluate(bishopAndKnight);
        assertTrue(gap >= 20, "expected a clear bishop pair bonus on top of the small material/PST gap, got " + gap);
    }

    @Test
    void aRookOnAnOpenFileScoresBetterThanOneBehindItsOwnPawn() {
        Board openFile = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a2"), Piece.of(PieceType.PAWN, Color.WHITE));
        Board blockedFile = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d2"), Piece.of(PieceType.PAWN, Color.WHITE));

        assertTrue(EnrichedPositionEvaluator.evaluate(openFile) > EnrichedPositionEvaluator.evaluate(blockedFile),
                "a rook on a fully open file should score better than the same rook behind its own pawn");
    }

    @Test
    void doubledPawnsAreWorseThanTheSamePawnCountSpreadOverTwoFiles() {
        Board doubled = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("c2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("c3"), Piece.of(PieceType.PAWN, Color.WHITE));
        Board spread = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("c2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d3"), Piece.of(PieceType.PAWN, Color.WHITE));

        assertTrue(EnrichedPositionEvaluator.evaluate(doubled) < EnrichedPositionEvaluator.evaluate(spread),
                "two pawns doubled on the same file should score worse than the same two pawns on separate, mutually supporting files");
    }

    @Test
    void isolatedPawnsAreWorseThanTwoPawnsThatSupportEachOther() {
        // Sommes de table positionnelle identiques (a+c = a+b = 15, meme rangee) : seule
        // l'isolation differe entre les deux plateaux.
        Board isolated = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("c2"), Piece.of(PieceType.PAWN, Color.WHITE));
        Board supported = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("b2"), Piece.of(PieceType.PAWN, Color.WHITE));

        assertTrue(EnrichedPositionEvaluator.evaluate(isolated) < EnrichedPositionEvaluator.evaluate(supported),
                "two isolated pawns should score worse than two pawns on adjacent files that support each other");
    }

    @Test
    void anUnopposedPassedPawnScoresBetterThanOneOnAFileNextToAnEnemyPawn() {
        // Meme pion noir fixe (d7) dans les deux plateaux, seul le fichier du pion blanc
        // change (b6, a 2 colonnes de d : non bloque ; c6, adjacent a d : bloque) - isole la
        // bonification "passe" sans changer le materiel d'aucun camp.
        Board passed = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("b6"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d7"), Piece.of(PieceType.PAWN, Color.BLACK));
        Board blocked = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("c6"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d7"), Piece.of(PieceType.PAWN, Color.BLACK));

        assertTrue(EnrichedPositionEvaluator.evaluate(passed) > EnrichedPositionEvaluator.evaluate(blocked),
                "a pawn with no enemy pawn on its file or an adjacent one should score better than one next to an enemy pawn");
    }

    @Test
    void aKingWithAnIntactPawnShieldIsSaferThanOneWithPawnsFarAway() {
        // f2/g2/h2 (bouclier) et a2/b2/c2 (loin du roi) ont la meme somme de table
        // positionnelle (5+10+10) sur la rangee 2 - seule la protection du roi differe. Une
        // dame de chaque cote fait monter la phase au-dessus de 0 (sinon le terme de securite,
        // pondere par la phase, resterait nul).
        Board shielded = Board.empty()
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.QUEEN, Color.BLACK))
                .withPiece(Position.fromAlgebraic("f2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h2"), Piece.of(PieceType.PAWN, Color.WHITE));
        Board exposed = Board.empty()
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.QUEEN, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("b2"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("c2"), Piece.of(PieceType.PAWN, Color.WHITE));

        assertTrue(EnrichedPositionEvaluator.evaluate(shielded) > EnrichedPositionEvaluator.evaluate(exposed),
                "a king with an intact pawn shield should score better than one whose pawns are far from it");
    }

    @Test
    void centralizingTheKingIsRewardedMoreOnceMaterialHasBeenTradedOff() {
        Board richCentral = richBoardWithWhiteKingOn(Position.fromAlgebraic("e4"));
        Board richCorner = richBoardWithWhiteKingOn(Position.fromAlgebraic("a1"));
        Board bareCentral = bareBoardWithWhiteKingOn(Position.fromAlgebraic("e4"));
        Board bareCorner = bareBoardWithWhiteKingOn(Position.fromAlgebraic("a1"));

        int richDelta = EnrichedPositionEvaluator.evaluate(richCentral) - EnrichedPositionEvaluator.evaluate(richCorner);
        int bareDelta = EnrichedPositionEvaluator.evaluate(bareCentral) - EnrichedPositionEvaluator.evaluate(bareCorner);

        assertTrue(bareDelta > richDelta,
                "a centralized king should gain relative to a cornered one once material is traded off (tapered eval), "
                        + "richDelta=" + richDelta + " bareDelta=" + bareDelta);
    }

    /** Memes 7 pieces non-royales (2C+2F+2T+1D par camp, phase = 24) quel que soit whiteKing. */
    private static Board richBoardWithWhiteKingOn(Position whiteKing) {
        return Board.empty()
                .withPiece(whiteKing, Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("b1"), Piece.of(PieceType.KNIGHT, Color.WHITE))
                .withPiece(Position.fromAlgebraic("c1"), Piece.of(PieceType.BISHOP, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f1"), Piece.of(PieceType.BISHOP, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KNIGHT, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("b8"), Piece.of(PieceType.KNIGHT, Color.BLACK))
                .withPiece(Position.fromAlgebraic("c8"), Piece.of(PieceType.BISHOP, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.QUEEN, Color.BLACK))
                .withPiece(Position.fromAlgebraic("f8"), Piece.of(PieceType.BISHOP, Color.BLACK))
                .withPiece(Position.fromAlgebraic("g8"), Piece.of(PieceType.KNIGHT, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.ROOK, Color.BLACK));
    }

    /** Rois seuls (phase = 0), whiteKing variable, meme carre noir fixe que richBoardWithWhiteKingOn. */
    private static Board bareBoardWithWhiteKingOn(Position whiteKing) {
        return Board.empty()
                .withPiece(whiteKing, Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));
    }
}
