package com.guesschess.application.computer;

import com.guesschess.application.computer.GuessAwareAgent.Config;
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
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuessAwareAgentTest {

    /** Budget de temps enorme : les tests ne doivent jamais dependre de la vitesse de la machine. */
    private static final Config ROOMY = new Config(3, 2, 60_000);

    private static Position at(String algebraic) {
        return Position.fromAlgebraic(algebraic);
    }

    private static RandomGenerator seeded(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    private static AgentSession session(Config config, Color color, GameVariant variant, long seed) {
        return new GuessAwareAgent(BuiltInAgents.GUESSAWARE_V1, config)
                .newSession(new AgentSessionContext(color, variant, "test", seeded(seed)));
    }

    private static List<Move> legal(Board board) {
        return MoveGenerator.generateLegalMoves(board, board.sideToMove());
    }

    private static Board sacrificePosition() {
        return Board.empty()
                .withPiece(at("g1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(at("d1"), Piece.of(PieceType.QUEEN, Color.WHITE))
                .withPiece(at("h7"), Piece.of(PieceType.KING, Color.BLACK))
                .withPiece(at("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(at("d8"), Piece.of(PieceType.KNIGHT, Color.BLACK));
    }

    /** Roi blanc en a1 en echec (laisse ainsi par un round annule) : Txa1 fait partie des coups noirs. */
    private static Board whiteKingLeftInCheck() {
        return Board.empty()
                .withPiece(at("a1"), Piece.of(PieceType.KING, Color.WHITE))
                .withPiece(at("a8"), Piece.of(PieceType.ROOK, Color.BLACK))
                .withPiece(at("h8"), Piece.of(PieceType.KING, Color.BLACK))
                .withSideToMove(Color.BLACK);
    }

    @Test
    void coexistsWithMinimaxInTheRegistry() {
        AgentRegistry registry = new AgentRegistry();
        BuiltInAgents.registerMinimaxV1(registry);
        BuiltInAgents.registerGuessAwareV1(registry);

        assertEquals("guessaware@1", registry.create(registry.resolve("guessaware@1"), ComputerLevel.HARD).id().toString());
        assertEquals("minimax@1", registry.create(registry.resolve("minimax@1"), ComputerLevel.HARD).id().toString());
    }

    @Test
    void movesAndGuessesAreLegalAndReproducibleForAGivenSeed() {
        Board board = Board.initial();
        List<Move> legal = legal(board);

        Move move = session(ROOMY, Color.WHITE, GameVariant.GUESSCHESS, 5).move(board, legal);
        Move sameMove = session(ROOMY, Color.WHITE, GameVariant.GUESSCHESS, 5).move(board, legal);
        Move guess = session(ROOMY, Color.BLACK, GameVariant.GUESSCHESS, 5).guess(board, legal);

        assertTrue(legal.contains(move));
        assertEquals(move, sameMove);
        assertTrue(legal.contains(guess));
    }

    /**
     * Le sacrifice Dxd8 (voir GuessAwareSearchTest) est jouable pour guessaware@1 : tire de
     * temps en temps (strategie mixte), mais pas systematiquement - il reste un coup parmi
     * d'autres, meme si un minimax classique ne le jouerait jamais.
     */
    @Test
    void sometimesPlaysTheSacrificeButNotAlways() {
        Board board = sacrificePosition();
        List<Move> legal = legal(board);
        int sacrifices = 0;
        for (long seed = 1; seed <= 40; seed++) {
            Move move = session(ROOMY, Color.WHITE, GameVariant.GUESSCHESS, seed).move(board, legal);
            if (move.from().equals(at("d1")) && move.to().equals(at("d8"))) {
                sacrifices++;
            }
        }
        assertTrue(sacrifices > 0 && sacrifices < 40, "sacrifices: " + sacrifices + "/40");
    }

    @Test
    void neverPlaysAnAvoidableKingCapture() {
        Board board = whiteKingLeftInCheck();
        List<Move> legal = legal(board);
        assertTrue(legal.stream().anyMatch(m -> m.isCapture() && m.capturedPiece().type() == PieceType.KING));

        List<Move> chosen = new ArrayList<>();
        for (long seed = 1; seed <= 30; seed++) {
            chosen.add(session(new Config(2, 1, 60_000), Color.BLACK, GameVariant.GUESSCHESS, seed).move(board, legal));
        }

        assertFalse(chosen.stream().anyMatch(m -> m.isCapture() && m.capturedPiece().type() == PieceType.KING));
    }

    @Test
    void alwaysGuessesTheCaptureOfItsOwnKing() {
        Board board = whiteKingLeftInCheck();
        List<Move> legal = legal(board);

        for (long seed = 1; seed <= 10; seed++) {
            Move guess = session(new Config(2, 1, 60_000), Color.WHITE, GameVariant.GUESSCHESS, seed).guess(board, legal);
            assertTrue(guess.isCapture() && guess.capturedPiece().type() == PieceType.KING, "guessed " + guess);
        }
    }

    /** Budget nul : seule la profondeur 1 (toujours menee a terme) repond - un coup legal quand meme. */
    @Test
    void anExhaustedBudgetStillAnswersWithAtLeastTheShallowestSearch() {
        Board board = Board.initial();
        List<Move> legal = legal(board);

        Move move = session(new Config(4, 3, 0), Color.WHITE, GameVariant.GUESSCHESS, 1).move(board, legal);

        assertTrue(legal.contains(move));
    }
}
