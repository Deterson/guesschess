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

class GameTest {

    @Test
    void newGameStartsOngoingWithWhiteToMoveAndTwentyLegalMoves() {
        Game game = Game.newGame();

        assertEquals(GameStatus.ONGOING, game.status());
        assertEquals(Color.WHITE, game.sideToMove());
        assertEquals(20, game.legalMoves().size());
        assertNull(game.result());
    }

    @Test
    void makeMoveRejectsAMoveThatIsNotLegal() {
        Game game = Game.newGame();
        Piece whitePawn = Piece.of(PieceType.PAWN, Color.WHITE);
        Move impossibleMove = Move.normal(Position.fromAlgebraic("e2"), Position.fromAlgebraic("e5"), whitePawn, null);

        assertThrows(IllegalArgumentException.class, () -> game.makeMove(impossibleMove));
    }

    @Test
    void makeMoveRejectsAMoveForTheSideNotToMove() {
        Game game = Game.newGame();
        Piece blackPawn = Piece.of(PieceType.PAWN, Color.BLACK);
        Move blackMoveWhileWhiteToPlay = Move.doublePawnPush(
                Position.fromAlgebraic("e7"), Position.fromAlgebraic("e5"), blackPawn);

        assertThrows(IllegalArgumentException.class, () -> game.makeMove(blackMoveWhileWhiteToPlay));
    }

    @Test
    void foolsMateEndsInCheckmateWonByBlack() {
        Game game = Game.newGame();

        play(game, "f2", "f3");
        play(game, "e7", "e5");
        play(game, "g2", "g4");
        play(game, "d8", "h4");

        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.CHECKMATE, game.result().cause());
        assertEquals(Color.BLACK, game.result().winner());
        assertTrue(game.legalMoves().isEmpty());
    }

    @Test
    void kingAndQueenVersusKingStalematePosition() {
        Board beforeQueenMove = Board.empty()
                .withPiece(Position.fromAlgebraic("f7"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("g5"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h8"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.WHITE);
        Game game = Game.fromPosition(beforeQueenMove);

        play(game, "g5", "g6");

        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.STALEMATE, game.result().cause());
        assertNull(game.result().winner());
        assertFalse(game.isInCheck());
    }

    @Test
    void enPassantCaptureIsPlayableDuringARealGame() {
        Game game = Game.newGame();

        play(game, "e2", "e4");
        play(game, "a7", "a6");
        play(game, "e4", "e5");
        play(game, "d7", "d5");

        List<Move> whiteMoves = game.legalMoves();
        Move enPassant = findMove(whiteMoves, "e5", "d6");
        game.makeMove(enPassant);

        assertNull(game.board().pieceAt(Position.fromAlgebraic("d5")));
        assertEquals(Piece.of(PieceType.PAWN, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("d6")));
    }

    @Test
    void promotionDuringARealGameReplacesThePawn() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a7"), Piece.of(PieceType.PAWN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK));
        Game game = Game.fromPosition(board);

        Move promotion = game.legalMoves().stream()
                .filter(m -> m.from().equals(Position.fromAlgebraic("a7")) && m.promotionType() == PieceType.QUEEN)
                .findFirst()
                .orElseThrow();
        game.makeMove(promotion);

        assertEquals(Piece.of(PieceType.QUEEN, Color.WHITE), game.board().pieceAt(Position.fromAlgebraic("a8")));
    }

    @Test
    void fiftyMoveRuleEndsGameInADraw() {
        Board setup = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("b1"), Piece.of(PieceType.KNIGHT, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));
        Board nearLimit = shuffleToHalfmoveClock(setup, 99);
        assertEquals(99, nearLimit.halfmoveClock());

        Game game = Game.fromPosition(nearLimit);
        Move anyMove = game.legalMoves().get(0);
        game.makeMove(anyMove);

        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.DRAW_FIFTY_MOVE_RULE, game.result().cause());
        assertNull(game.result().winner());
    }

    @Test
    void threefoldRepetitionEndsGameInADraw() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("b1"), Piece.of(PieceType.KNIGHT, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("b8"), Piece.of(PieceType.KNIGHT, Color.BLACK));
        Game game = Game.fromPosition(board);

        // position (knights home, white to move) occurs before any move (1st), after the
        // first full round-trip (2nd), and after the second round-trip (3rd) -> repetition
        for (int i = 0; i < 2; i++) {
            play(game, "b1", "c3");
            play(game, "b8", "c6");
            play(game, "c3", "b1");
            play(game, "c6", "b8");
        }

        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.DRAW_THREEFOLD_REPETITION, game.result().cause());
        assertNull(game.result().winner());
    }

    @Test
    void insufficientMaterialEndsGameInADraw() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("e1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("e8"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a1"), Piece.of(PieceType.BISHOP, Color.WHITE));
        Game game = Game.fromPosition(board);

        Move kingShuffle = findMove(game.legalMoves(), "e1", "d1");
        game.makeMove(kingShuffle);

        assertEquals(GameStatus.FINISHED, game.status());
        assertEquals(GameResultCause.DRAW_INSUFFICIENT_MATERIAL, game.result().cause());
    }

    @Test
    void cannotPlayAfterGameIsFinished() {
        Game game = Game.newGame();
        play(game, "f2", "f3");
        play(game, "e7", "e5");
        play(game, "g2", "g4");
        Move mate = findMove(game.legalMoves(), "d8", "h4");
        game.makeMove(mate);

        assertThrows(IllegalStateException.class, () -> game.makeMove(mate));
    }

    private static void play(Game game, String from, String to) {
        game.makeMove(findMove(game.legalMoves(), from, to));
    }

    private static Move findMove(List<Move> moves, String from, String to) {
        Position fromPos = Position.fromAlgebraic(from);
        Position toPos = Position.fromAlgebraic(to);
        return moves.stream()
                .filter(m -> m.from().equals(fromPos) && m.to().equals(toPos))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no legal move " + from + "-" + to));
    }

    private static Board shuffleToHalfmoveClock(Board board, int plies) {
        Position knightHome = Position.fromAlgebraic("b1");
        Position knightAway = Position.fromAlgebraic("c3");
        Position kingHome = Position.fromAlgebraic("a8");
        Position kingAway = Position.fromAlgebraic("a7");

        Board current = board;
        boolean knightAtHome = true;
        boolean kingAtHome = true;
        for (int i = 0; i < plies; i++) {
            if (current.sideToMove() == Color.WHITE) {
                Position from = knightAtHome ? knightHome : knightAway;
                Position to = knightAtHome ? knightAway : knightHome;
                Piece knight = current.pieceAt(from);
                current = current.applyMove(Move.normal(from, to, knight, null));
                knightAtHome = !knightAtHome;
            } else {
                Position from = kingAtHome ? kingHome : kingAway;
                Position to = kingAtHome ? kingAway : kingHome;
                Piece king = current.pieceAt(from);
                current = current.applyMove(Move.normal(from, to, king, null));
                kingAtHome = !kingAtHome;
            }
        }
        return current;
    }
}
