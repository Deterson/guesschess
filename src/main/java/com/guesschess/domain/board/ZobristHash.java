package com.guesschess.domain.board;

import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Hachage Zobrist d'une position (etape 24, table de transposition) : identifie une Board
 * sur les memes champs que Board.equals/hashCode (pieces, trait, droits de roque, case en
 * passant - jamais les compteurs de coups, deja exclus par Board pour la meme raison, ce
 * n'est pas ce qui distingue deux positions aux echecs). Recalcule entierement a chaque
 * appel plutot que maintenu incrementalement par Board.applyMove : le cout (64 cases et
 * quelques XOR) reste marginal a cote de la generation de coups deja faite a chaque noeud
 * de recherche, pour zero changement a Board (classe par ailleurs largement testee).
 */
public final class ZobristHash {

    private ZobristHash() {
    }

    /** Seed fixe : hash reproductible d'un run a l'autre, comme le RNG seede de MinimaxChessEngine. */
    private static final long SEED = 0x5DEECE66DL;

    private static final int PIECE_TYPE_COUNT = PieceType.values().length;
    private static final int COLOR_COUNT = Color.values().length;

    private static final long[][][] PIECE_SQUARE = new long[64][PIECE_TYPE_COUNT][COLOR_COUNT];
    private static final long SIDE_TO_MOVE;
    private static final long[] CASTLING_RIGHT = new long[4];
    private static final long[] EN_PASSANT_FILE = new long[8];

    static {
        RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(SEED);
        for (int square = 0; square < 64; square++) {
            for (int type = 0; type < PIECE_TYPE_COUNT; type++) {
                for (int color = 0; color < COLOR_COUNT; color++) {
                    PIECE_SQUARE[square][type][color] = rng.nextLong();
                }
            }
        }
        SIDE_TO_MOVE = rng.nextLong();
        for (int i = 0; i < CASTLING_RIGHT.length; i++) {
            CASTLING_RIGHT[i] = rng.nextLong();
        }
        for (int i = 0; i < EN_PASSANT_FILE.length; i++) {
            EN_PASSANT_FILE[i] = rng.nextLong();
        }
    }

    public static long hash(Board board) {
        long hash = 0L;
        Piece[] squares = board.squaresSnapshot();
        for (int square = 0; square < squares.length; square++) {
            Piece piece = squares[square];
            if (piece != null) {
                hash ^= PIECE_SQUARE[square][piece.type().ordinal()][piece.color().ordinal()];
            }
        }
        if (board.sideToMove() == Color.BLACK) {
            hash ^= SIDE_TO_MOVE;
        }
        CastlingRights rights = board.castlingRights();
        if (rights.whiteKingside()) {
            hash ^= CASTLING_RIGHT[0];
        }
        if (rights.whiteQueenside()) {
            hash ^= CASTLING_RIGHT[1];
        }
        if (rights.blackKingside()) {
            hash ^= CASTLING_RIGHT[2];
        }
        if (rights.blackQueenside()) {
            hash ^= CASTLING_RIGHT[3];
        }
        Position enPassantTarget = board.enPassantTarget();
        if (enPassantTarget != null) {
            hash ^= EN_PASSANT_FILE[enPassantTarget.file()];
        }
        return hash;
    }
}
