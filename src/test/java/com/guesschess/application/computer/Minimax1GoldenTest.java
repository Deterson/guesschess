package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests "dores" de minimax@1 (etape 20) : coups attendus enregistres AVANT le refactor en
 * registre de versions (moteur de l'etape 17, MEDIUM/HARD, deterministes). Une version
 * publiee est immuable : si un de ces tests casse, ce n'est pas le test qu'il faut changer
 * mais une nouvelle version d'agent qu'il faut creer. Passe par le vrai chemin
 * (registre -> agent -> session) pour couvrir aussi le cablage.
 */
class Minimax1GoldenTest {

    private static final GuessAgentSupplier AGENTS = level -> BuiltInAgents.registerMinimaxV1(new AgentRegistry())
            .create(BuiltInAgents.MINIMAX_V1, level);

    private interface GuessAgentSupplier {
        GuessAgent forLevel(ComputerLevel level);
    }

    private static String move(Board board, ComputerLevel level, long seed) {
        AgentSession session = AGENTS.forLevel(level).newSession(new AgentSessionContext(
                board.sideToMove(), GameVariant.GUESSMATE, "golden", seeded(seed)));
        List<Move> legal = MoveGenerator.generateLegalMoves(board, board.sideToMove());
        Move m = session.move(board, legal);
        return m.from().toString() + m.to();
    }

    private static java.util.random.RandomGenerator seeded(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    @Test
    void startingPosition() {
        assertEquals("d2d4", move(Board.initial(), ComputerLevel.MEDIUM, 1));
        assertEquals("b1c3", move(Board.initial(), ComputerLevel.HARD, 1));
    }

    @Test
    void italianMiddlegame() {
        Board board = play(Board.initial(), "e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "f8c5", "c2c3", "g8f6", "d2d3", "d7d6");
        assertEquals("c1g5", move(board, ComputerLevel.MEDIUM, 1));
        assertEquals("c4d5", move(board, ComputerLevel.HARD, 1));
    }

    @Test
    void queenThatMustNotHangItself() {
        assertEquals("d1d3", move(queenPosition(), ComputerLevel.MEDIUM, 1));
        assertEquals("d1d5", move(queenPosition(), ComputerLevel.HARD, 1));
    }

    @Test
    void mateInOne() {
        Board board = Board.empty()
                .withPiece(Position.fromAlgebraic("c6"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("b1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.KING, Color.BLACK));
        assertEquals("b1b7", move(board, ComputerLevel.MEDIUM, 1));
        assertEquals("b1b7", move(board, ComputerLevel.HARD, 1));
    }

    /** Facile joue au hasard pondere : avec la meme graine, les memes choix (partie rejouable). */
    @Test
    void easyIsReproducibleForAGivenSeed() {
        Board board = Board.initial();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        for (long seed = 1; seed <= 6; seed++) {
            first.add(move(board, ComputerLevel.EASY, seed));
            second.add(move(board, ComputerLevel.EASY, seed));
        }
        assertEquals(first, second);
    }

    private static Board queenPosition() {
        return Board.empty()
                .withPiece(Position.fromAlgebraic("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(Position.fromAlgebraic("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(Position.fromAlgebraic("h7"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(Position.fromAlgebraic("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(Position.fromAlgebraic("d8"), Piece.of(PieceType.KNIGHT, Color.BLACK));
    }

    private static Board play(Board board, String... uciMoves) {
        Board current = board;
        for (String uci : uciMoves) {
            Board from = current;
            Move move = MoveGenerator.generateLegalMoves(from, from.sideToMove()).stream()
                    .filter(m -> (m.from().toString() + m.to()).equals(uci))
                    .findFirst().orElseThrow(() -> new IllegalStateException("illegal " + uci));
            current = from.applyMove(move);
        }
        return current;
    }
}
