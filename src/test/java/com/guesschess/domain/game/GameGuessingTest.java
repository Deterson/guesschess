package com.guesschess.domain.game;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mecanique de devinette (etape 2 de la roadmap) : soumission du coup reel et de la
 * devinette, resolution du round, et cas particulier du roi laisse en echec.
 */
class GameGuessingTest {

    @Test
    void correctGuessCancelsTheMoveAndPassesTurnWithoutChangingTheBoard() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitGuess(e4);

        RoundResult result = game.submitMove(e4);

        assertFalse(result.movePlayed());
        assertTrue(result.guessedCorrectly());
        assertEquals(Color.WHITE, result.mover());
        assertEquals(Color.BLACK, result.guesser());
        assertEquals(Piece.of(PieceType.PAWN, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("e2")));
        assertNull(game.board().pieceAt(Position.fromAlgebraic("e4")));
        assertEquals(Color.BLACK, game.sideToMove());
        assertTrue(game.moveHistory().isEmpty());
        assertEquals(GameStatus.ONGOING, game.status());
        assertNull(game.pendingGuess());
    }

    @Test
    void incorrectGuessPlaysTheMoveNormally() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        Move wrongGuess = findMove(game.legalMoves(), "d2", "d4");
        game.submitGuess(wrongGuess);

        RoundResult result = game.submitMove(e4);

        assertTrue(result.movePlayed());
        assertFalse(result.guessedCorrectly());
        assertNull(game.board().pieceAt(Position.fromAlgebraic("e2")));
        assertEquals(Piece.of(PieceType.PAWN, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("e4")));
        assertEquals(Color.BLACK, game.sideToMove());
        assertEquals(1, game.moveHistory().size());
    }

    @Test
    void noGuessSubmittedPlaysTheMoveNormally() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");

        RoundResult result = game.submitMove(e4);

        assertTrue(result.movePlayed());
        assertFalse(result.guessedCorrectly());
        assertNull(result.guessedMove());
    }

    @Test
    void guessCanBeOverriddenUntilMoveIsSubmitted() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        Move d4 = findMove(game.legalMoves(), "d2", "d4");
        game.submitGuess(d4);
        game.submitGuess(e4);

        RoundResult result = game.submitMove(e4);

        assertTrue(result.guessedCorrectly());
        assertEquals(e4, result.guessedMove());
    }

    @Test
    void pendingGuessResetsAfterRoundResolvesAndIsNotCarriedOver() {
        Game game = Game.newGame();
        Move e4 = findMove(game.legalMoves(), "e2", "e4");
        game.submitGuess(e4);
        game.submitMove(e4);

        assertNull(game.pendingGuess());
        Move blackMove = findMove(game.legalMoves(), "e7", "e5");
        RoundResult result = game.submitMove(blackMove);

        assertTrue(result.movePlayed());
        assertNull(result.guessedMove());
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
        Game game = Game.fromPosition(checkWithSingleEscapePosition());
        assertTrue(game.isInCheck());
        assertEquals(List.of(Position.fromAlgebraic("b1")),
                game.legalMoves().stream().map(Move::to).toList());

        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        RoundResult result = game.submitMove(escape);

        assertFalse(result.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.BLACK, game.sideToMove());
        assertEquals(Piece.of(PieceType.KING, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("a1")));
        assertTrue(game.isInCheck(Color.WHITE));

        Move captureKing = findMove(game.legalMoves(), "a8", "a1");
        RoundResult second = game.submitMove(captureKing);

        assertTrue(second.movePlayed());
        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.KING_CAPTURED, game.result().cause());
        assertEquals(Color.BLACK, game.result().winner());
    }

    @Test
    void guesserCanChooseNotToCaptureTheHangingKing() {
        Game game = Game.fromPosition(checkWithSingleEscapePosition());
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        game.submitMove(escape);

        Move harmless = findMove(game.legalMoves(), "h8", "h7");
        RoundResult result = game.submitMove(harmless);

        assertTrue(result.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.WHITE, game.sideToMove());
        assertTrue(game.isInCheck());
        assertTrue(game.legalMoves().stream().allMatch(m -> m.from().equals(Position.fromAlgebraic("a1"))));

        Move finalEscape = findMove(game.legalMoves(), "a1", "b1");
        RoundResult third = game.submitMove(finalEscape);

        assertTrue(third.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertFalse(game.isInCheck());
    }

    @Test
    void freeKingCaptureCanItselfBeGuessedAndCancelled() {
        Game game = Game.fromPosition(checkWithSingleEscapePosition());
        Move escape = findMove(game.legalMoves(), "a1", "b1");
        game.submitGuess(escape);
        game.submitMove(escape);

        Move captureKing = findMove(game.legalMoves(), "a8", "a1");
        game.submitGuess(captureKing);
        RoundResult result = game.submitMove(captureKing);

        assertFalse(result.movePlayed());
        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.WHITE, game.sideToMove());
        assertEquals(Piece.of(PieceType.KING, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("a1")));
        assertTrue(game.isInCheck());
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

    private static void play(Game game, String from, String to) {
        game.submitMove(findMove(game.legalMoves(), from, to));
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
