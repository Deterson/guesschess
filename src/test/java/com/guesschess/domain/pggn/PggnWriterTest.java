package com.guesschess.domain.pggn;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameResultCause;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serialisation PGGN (etape 10 de la roadmap) : les exemples reproduisent
 * litteralement ceux de CLAUDE.md (round joue avec devinette fausse, round annule par
 * une devinette correcte, cas terminal Guessmate).
 */
class PggnWriterTest {

    @Test
    void moveWithoutAnyGuessHasNoParentheses() {
        Game game = Game.newGame();
        submitRound(game, "e2", "e4", null, null);
        submitRound(game, "e7", "e5", null, null);
        submitRound(game, "g1", "f3", null, null);

        String pggn = PggnWriter.write(game);

        assertTrue(pggn.contains("1. e4 e5 2. Nf3"), pggn);
    }

    @Test
    void wrongGuessesAreShownInParenthesesAfterTheRealMove() {
        Game game = Game.newGame();
        submitRound(game, "e2", "e4", "e2", "e3");
        submitRound(game, "e7", "e5", "b8", "c6");

        String pggn = PggnWriter.write(game);

        assertTrue(pggn.contains("1. e4(e3) e5(Nc6)"), pggn);
    }

    @Test
    void correctGuessCancelsTheRoundAndShowsOnlyTheGuessInParentheses() {
        Game game = Game.newGame();
        submitRound(game, "e2", "e4", "e2", "e3");
        submitRound(game, "e7", "e5", "b8", "c6");
        submitRound(game, "g1", "f3", "g1", "f3"); // devinette correcte -> round annule
        submitRound(game, "b8", "c6", "a7", "a5"); // le trait est reste a noir (round annule)

        String pggn = PggnWriter.write(game);

        assertTrue(pggn.contains("1. e4(e3) e5(Nc6)"), pggn);
        assertTrue(pggn.contains("2. (Nf3) Nc6(a5)"), pggn);
    }

    @Test
    void guessmateShowsOnlyTheGuessWithAHardcodedMateSuffix() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a5"), Piece.of(PieceType.BISHOP, Color.BLACK))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK));
        Game game = Game.fromPosition(board, GameVariant.GUESSCHESS);

        Move ke2 = findMove(game.legalMoves(), "e1", "e2");
        game.submitMove(ke2);
        game.submitGuess(ke2);

        String pggn = PggnWriter.write(game);

        assertTrue(pggn.contains("1. (Ke2)#"), pggn);
        assertTrue(pggn.contains("[Result \"0-1\"]"), pggn);
        assertTrue(pggn.contains("[Termination \"" + GameResultCause.CHECK_PARRY_GUESSED + "\"]"), pggn);
    }

    @Test
    void headersDefaultToPlaceholdersAndReflectVariantAndOngoingResult() {
        Game game = Game.newGame(GameVariant.GUESSCHESS);

        String pggn = PggnWriter.write(game);

        assertTrue(pggn.contains("[Event \"?\"]"), pggn);
        assertTrue(pggn.contains("[Date \"?\"]"), pggn);
        assertTrue(pggn.contains("[White \"?\"]"), pggn);
        assertTrue(pggn.contains("[Black \"?\"]"), pggn);
        assertTrue(pggn.contains("[Variant \"GUESSCHESS\"]"), pggn);
        assertTrue(pggn.contains("[Result \"*\"]"), pggn);
        assertTrue(pggn.contains("[Termination \"?\"]"), pggn);
    }

    @Test
    void headerOverridesAreUsedInsteadOfPlaceholders() {
        Game game = Game.newGame();

        String pggn = PggnWriter.write(game, java.util.Map.of("White", "alice", "Black", "bob", "Date", "2026.08.31"));

        assertTrue(pggn.contains("[White \"alice\"]"), pggn);
        assertTrue(pggn.contains("[Black \"bob\"]"), pggn);
        assertTrue(pggn.contains("[Date \"2026.08.31\"]"), pggn);
    }

    /**
     * Soumet un round complet : coup reel (moveFrom-moveTo) et, si guessFrom non
     * null, une devinette (guessFrom-guessTo) parmi les coups legaux du joueur au
     * trait (celui dont c'est le coup reel - la devinette porte sur les memes coups).
     */
    private static void submitRound(Game game, String moveFrom, String moveTo, String guessFrom, String guessTo) {
        List<Move> legalMoves = game.legalMoves();
        Move move = findMove(legalMoves, moveFrom, moveTo);
        Move guess = guessFrom == null ? null : findMove(legalMoves, guessFrom, guessTo);
        game.submitMove(move);
        game.submitGuess(guess);
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
