package com.guesschess.domain.game;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mecanique de devinette (etape 2 de la roadmap) : un round attend obligatoirement
 * les deux soumissions (coup reel + devinette, celle-ci pouvant valoir "aucune")
 * avant de se resoudre, quel que soit leur ordre d'arrivee ; et cas particulier du
 * roi laisse en echec.
 */
class GameGuessingTest {

    @Test
    void moveWaitsForTheGuessWhenItArrivesFirst() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");

        Optional<RoundResult> result = game.submitMove(e4);

        assertTrue(result.isEmpty());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.WHITE, game.sideToMove());
        assertTrue(game.moveHistory().isEmpty());
    }

    @Test
    void guessWaitsForTheMoveWhenItArrivesFirst() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");

        Optional<RoundResult> result = game.submitGuess(e4);

        assertTrue(result.isEmpty());
        assertEquals(GameStatus.ONGOING, game.status());
    }

    @Test
    void explicitNoGuessStillCompletesTheRoundOnceTheMoveArrives() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");

        assertTrue(game.submitMove(e4).isEmpty());
        RoundResult result = game.submitGuess(null).orElseThrow();

        assertTrue(result.movePlayed());
        assertFalse(result.guessedCorrectly());
        assertEquals(Piece.of(PieceType.PAWN, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("e4")));
    }

    @Test
    void correctGuessCancelsTheMoveAndPassesTurnWithoutChangingTheBoard() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitGuess(e4);

        RoundResult result = game.submitMove(e4).orElseThrow();

        assertFalse(result.movePlayed());
        assertTrue(result.guessedCorrectly());
        assertEquals(Color.WHITE, result.mover());
        assertEquals(Color.BLACK, result.guesser());
        assertEquals(Piece.of(PieceType.PAWN, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("e2")));
        assertEquals(Color.BLACK, game.sideToMove());
        assertTrue(game.moveHistory().isEmpty());
        assertEquals(GameStatus.ONGOING, game.status());
    }

    @Test
    void incorrectGuessPlaysTheMoveNormally() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        Move wrongGuess = findMove(game.legalMoves(), "d2", "d4");
        game.submitGuess(wrongGuess);

        RoundResult result = game.submitMove(e4).orElseThrow();

        assertTrue(result.movePlayed());
        assertFalse(result.guessedCorrectly());
        assertEquals(Piece.of(PieceType.PAWN, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("e4")));
        assertEquals(Color.BLACK, game.sideToMove());
        assertEquals(1, game.moveHistory().size());
    }

    @Test
    void guessCanBeOverriddenUntilTheMoveArrives() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        Move d4 = findMove(game.legalMoves(), "d2", "d4");
        game.submitGuess(d4);
        game.submitGuess(e4);

        RoundResult result = game.submitMove(e4).orElseThrow();

        assertTrue(result.guessedCorrectly());
        assertEquals(e4, result.guessedMove());
    }

    @Test
    void mySubmissionIsNoneForBothColorsBeforeAnySubmission() {
        Game game = Game.newGame();

        assertFalse(game.mySubmission(Color.WHITE).submitted());
        assertFalse(game.mySubmission(Color.BLACK).submitted());
    }

    @Test
    void mySubmissionReflectsTheMoversOwnMoveButNeverTheGuessersToTheMover() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitMove(e4);

        assertTrue(game.mySubmission(Color.WHITE).submitted());
        assertEquals(e4, game.mySubmission(Color.WHITE).move());
        assertFalse(game.mySubmission(Color.BLACK).submitted());
    }

    @Test
    void mySubmissionReflectsTheGuessersOwnGuessButNeverTheMoversToTheGuesser() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitGuess(e4);

        assertTrue(game.mySubmission(Color.BLACK).submitted());
        assertEquals(e4, game.mySubmission(Color.BLACK).move());
        assertFalse(game.mySubmission(Color.WHITE).submitted());
    }

    @Test
    void mySubmissionDistinguishesExplicitNoGuessFromNothingSubmittedYet() {
        Game game = Game.newGame();

        assertFalse(game.mySubmission(Color.BLACK).submitted());

        game.submitGuess(null);

        assertTrue(game.mySubmission(Color.BLACK).submitted());
        assertNull(game.mySubmission(Color.BLACK).move());
    }

    @Test
    void mySubmissionIsResetForBothColorsOnceTheRoundResolves() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitGuess(e4);
        game.submitMove(e4);

        assertFalse(game.mySubmission(Color.WHITE).submitted());
        assertFalse(game.mySubmission(Color.BLACK).submitted());
    }

    @Test
    void moveCanBeOverriddenUntilTheGuessArrives() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        Move d4 = findMove(game.legalMoves(), "d2", "d4");
        game.submitMove(e4);
        game.submitMove(d4);

        RoundResult result = game.submitGuess(d4).orElseThrow();

        assertTrue(result.guessedCorrectly());
        assertEquals(d4, result.actualMove());
    }

    @Test
    void guessDoesNotCarryOverToTheNextRound() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitGuess(e4);
        game.submitMove(e4);

        Move blackMove = findMove(game.legalMoves(), "e7", "e5");
        assertTrue(game.submitMove(blackMove).isEmpty());
        RoundResult result = game.submitGuess(null).orElseThrow();

        assertTrue(result.movePlayed());
        assertFalse(result.guessedCorrectly());
    }

    @Test
    void submitGuessRejectsAMoveThatIsNotLegalForTheMover() {
        Game game = Game.newGame();
        Piece whitePawn = Piece.of(PieceType.PAWN, Color.WHITE);
        Move impossible = Move.normal(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e5"), whitePawn, null);

        assertThrows(IllegalArgumentException.class, () -> game.submitGuess(impossible));
    }

    @Test
    void submitGuessThrowsAfterGameIsFinished() {
        Game game = Game.newGame();
        play(game, "f2", "f3");
        play(game, "e7", "e5");
        play(game, "g2", "g4");
        play(game, "d8", "h4");
        assertEquals(GameStatus.FINISHED, game.status());

        Move anyMove = Move.doublePawnPush(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e4"),
                Piece.of(PieceType.PAWN, Color.WHITE));
        assertThrows(IllegalStateException.class, () -> game.submitGuess(anyMove));
    }

    @Test
    void correctlyGuessingTheEscapeFromCheckLeavesTheKingInCheckAndPassesTheTurn() {
        Game game = Game.fromPosition(checkWithMultipleEscapesPosition(), GameVariant.GUESSCHESS);
        assertTrue(game.isInCheck());
        assertEquals(List.of(Position.fromAlgebraic("b1"), Position.fromAlgebraic("b2")),
                game.legalMoves().stream().map(Move::to).toList());

        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        RoundResult result = game.submitMove(escape).orElseThrow();

        assertFalse(result.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.BLACK, game.sideToMove());
        assertEquals(Piece.of(PieceType.KING, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("a1")));
        assertTrue(game.isInCheck(Color.WHITE));

        Move captureKing = findMove(game.legalMoves(), "a8", "a1");
        game.submitMove(captureKing);
        RoundResult second = game.submitGuess(null).orElseThrow();

        assertTrue(second.movePlayed());
        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.KING_CAPTURED, game.result().cause());
        assertEquals(Color.BLACK, game.result().winner());
    }

    @Test
    void guesserCanChooseNotToCaptureTheHangingKing() {
        Game game = Game.fromPosition(checkWithMultipleEscapesPosition(), GameVariant.GUESSCHESS);
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        game.submitMove(escape);

        Move harmless = findMove(game.legalMoves(), "h8", "h7");
        game.submitMove(harmless);
        RoundResult result = game.submitGuess(null).orElseThrow();

        assertTrue(result.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.WHITE, game.sideToMove());
        assertTrue(game.isInCheck());
        assertTrue(game.legalMoves().stream().allMatch(m -> m.from().equals(Position.fromAlgebraic("a1"))));

        Move finalEscape = findMove(game.legalMoves(), "a1", "b1");
        game.submitMove(finalEscape);
        RoundResult third = game.submitGuess(null).orElseThrow();

        assertTrue(third.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertFalse(game.isInCheck());
    }

    @Test
    void freeKingCaptureCanItselfBeGuessedAndCancelled() {
        Game game = Game.fromPosition(checkWithMultipleEscapesPosition(), GameVariant.GUESSCHESS);
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        game.submitMove(escape);

        Move captureKing = findMove(game.legalMoves(), "a8", "a1");
        game.submitGuess(captureKing);
        RoundResult result = game.submitMove(captureKing).orElseThrow();

        assertFalse(result.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.WHITE, game.sideToMove());
        assertEquals(Piece.of(PieceType.KING, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("a1")));
        assertTrue(game.isInCheck());
    }

    @Test
    void guessmateVariantEndsTheGameInstantlyWhenTheCheckEscapeIsGuessedCorrectly() {
        Game game = Game.fromPosition(checkWithSingleEscapePosition(), GameVariant.GUESSMATE);
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);

        RoundResult result = game.submitMove(escape).orElseThrow();

        assertFalse(result.movePlayed());
        assertTrue(result.guessedCorrectly());
        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.CHECK_PARRY_GUESSED, game.result().cause());
        assertEquals(Color.BLACK, game.result().winner());
        assertEquals(Piece.of(PieceType.KING, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("a1")));
    }

    @Test
    void guessmateVariantBehavesLikeNoGuessmateWhenTheCorrectGuessIsNotAboutParryingCheck() {
        Game game = Game.newGame(GameVariant.GUESSMATE);
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitGuess(e4);

        RoundResult result = game.submitMove(e4).orElseThrow();

        assertFalse(result.movePlayed());
        assertTrue(result.guessedCorrectly());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.BLACK, game.sideToMove());
    }

    @Test
    void fastMateEndsTheGameInstantlyInGuesschessAsSoonAsTheOnlyEscapeFromCheckArises() {
        Game game = Game.fromPosition(checkWithSingleEscapePosition(), GameVariant.GUESSCHESS);

        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.KING_CAPTURED, game.result().cause());
        assertEquals(Color.BLACK, game.result().winner());
        assertEquals(Piece.of(PieceType.KING, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("a1")));
    }

    @Test
    void fastMateEndsTheGameInstantlyAssoonAsARealMoveLeavesTheOpponentWithOnlyOneEscapeFromCheck() {
        Game game = Game.fromPosition(aboutToDeliverSingleEscapeCheckPosition(), GameVariant.GUESSCHESS);
        assertFalse(game.isInCheck(Color.BLACK));
        Move deliverCheck = findMove(game.legalMoves(), "h4", "a4");

        game.submitGuess(null);
        RoundResult result = game.submitMove(deliverCheck).orElseThrow();

        assertTrue(result.movePlayed());
        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.KING_CAPTURED, game.result().cause());
        assertEquals(Color.WHITE, game.result().winner());
    }

    @Test
    void fastMateDeclaresADrawInsteadOfAWinWhenTheWinningSideHasInsufficientMatingMaterial() {
        Game game = Game.fromPosition(knightCheckWithSingleEscapeAndInsufficientMaterialPosition(), GameVariant.GUESSCHESS);

        assertEquals(GameStatus.FINISHED, game.status());
        assertTrue(game.result().isDraw());
        assertEquals(GameResultCause.DRAW_INSUFFICIENT_MATERIAL, game.result().cause());
    }

    @Test
    void fastMateDoesNotTriggerInGuesschessWhenSeveralEscapesFromCheckExist() {
        Game game = Game.fromPosition(checkWithMultipleEscapesPosition(), GameVariant.GUESSCHESS);
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);

        RoundResult result = game.submitMove(escape).orElseThrow();

        assertFalse(result.movePlayed());
        assertTrue(result.guessedCorrectly());
        assertEquals(GameStatus.ONGOING, game.status());
        assertTrue(game.isInCheck(Color.WHITE));
    }

    /**
     * Roi blanc en a1, en echec par la tour noire (colonne a), avec b2 tenu par le
     * cavalier noir : le seul coup legal blanc est Ra1-b1.
     */
    private static Board checkWithSingleEscapePosition() {
        return Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d3"), Piece.of(PieceType.KNIGHT, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));
    }

    /**
     * Blancs au trait (aucun echec) : la tour h4 n'est pas encore sur la colonne a. Une
     * fois jouee en a4, elle met le roi noir en echec le long de cette colonne, avec
     * b7 (pion noir) bloquant l'autre case adjacente : Rb8 devient le seul coup legal
     * noir - utilise pour verifier que fast_mate se declenche des l'application de ce
     * coup reel, sans attendre la moindre soumission du camp mis en echec.
     */
    private static Board aboutToDeliverSingleEscapeCheckPosition() {
        return Board.empty()
                .withPiece(Position.fromAlgebraic("h1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h4"), Piece.of(PieceType.ROOK, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("b7"), Piece.of(PieceType.PAWN, Color.BLACK));
    }

    /**
     * Meme echec que checkWithSingleEscapePosition, mais sans le cavalier tenant b2 :
     * le roi blanc a deux coups legaux (Ra1-b1 et Ra1-b2), donc une devinette juste
     * n'est plus forcement la seule possible (pas de fast_mate).
     */
    private static Board checkWithMultipleEscapesPosition() {
        return Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));
    }

    /**
     * Roi blanc g6 + cavalier f7 (echec au roi noir h8) contre seul roi noir : materiel
     * insuffisant pour mater (roi+cavalier contre roi, voir MaterialEvaluator), et le
     * roi blanc g6 couvre g7/h7 - Rg8 est le seul coup legal noir. Utilise pour verifier
     * que fast_mate declare la nulle plutot qu'une victoire dans ce cas (voir
     * how-to-play, endOfGameText2).
     */
    private static Board knightCheckWithSingleEscapeAndInsufficientMaterialPosition() {
        return Board.empty()
                .withPiece(Position.fromAlgebraic("g6"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("f7"), Piece.of(PieceType.KNIGHT, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);
    }

    /**
     * Joue un coup sans devinette (round resolu par un submitGuess(null) explicite),
     * pour les tests qui n'ont besoin que de faire avancer la partie normalement.
     */
    private static void play(Game game, String from, String to) {
        game.submitMove(findMove(game.legalMoves(), from, to));
        game.submitGuess(null);
    }

    private static Move findMove(List<Move> moves, String from, String to) {
        Position fromPos = Position.fromAlgebraic(from);
        Position toPos = Position.fromAlgebraic(to);
        return moves.stream()
                .filter(m -> m.from().equals(fromPos) && m.to().equals(toPos))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no legal move " + from + "-" + to));
    }
}
