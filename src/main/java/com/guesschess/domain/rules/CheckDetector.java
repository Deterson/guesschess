package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;

/**
 * Detection d'echec : une case est-elle attaquee, un roi est-il en echec.
 */
public final class CheckDetector {

    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] KNIGHT_DELTAS = {
            {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    private static final int[][] KING_DELTAS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    private CheckDetector() {
    }

    public static Position findKing(Board board, Color color) {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Position pos = Position.of(file, rank);
                Piece piece = board.pieceAt(pos);
                if (piece != null && piece.type() == PieceType.KING && piece.color() == color) {
                    return pos;
                }
            }
        }
        throw new IllegalStateException("no " + color + " king on the board");
    }

    public static boolean isInCheck(Board board, Color color) {
        return isSquareAttacked(board, findKing(board, color), color.opposite());
    }

    public static boolean isSquareAttacked(Board board, Position square, Color byColor) {
        return isAttackedByPawn(board, square, byColor)
                || isAttackedByKnight(board, square, byColor)
                || isAttackedByKing(board, square, byColor)
                || isAttackedBySlider(board, square, byColor, ROOK_DIRECTIONS, PieceType.ROOK)
                || isAttackedBySlider(board, square, byColor, BISHOP_DIRECTIONS, PieceType.BISHOP);
    }

    private static boolean isAttackedByPawn(Board board, Position square, Color byColor) {
        int rankOffset = byColor == Color.WHITE ? -1 : 1;
        for (int fileOffset : new int[]{-1, 1}) {
            if (!square.canTranslate(fileOffset, rankOffset)) {
                continue;
            }
            Position candidate = square.translate(fileOffset, rankOffset);
            Piece piece = board.pieceAt(candidate);
            if (piece != null && piece.color() == byColor && piece.type() == PieceType.PAWN) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAttackedByKnight(Board board, Position square, Color byColor) {
        for (int[] delta : KNIGHT_DELTAS) {
            if (!square.canTranslate(delta[0], delta[1])) {
                continue;
            }
            Position candidate = square.translate(delta[0], delta[1]);
            Piece piece = board.pieceAt(candidate);
            if (piece != null && piece.color() == byColor && piece.type() == PieceType.KNIGHT) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAttackedByKing(Board board, Position square, Color byColor) {
        for (int[] delta : KING_DELTAS) {
            if (!square.canTranslate(delta[0], delta[1])) {
                continue;
            }
            Position candidate = square.translate(delta[0], delta[1]);
            Piece piece = board.pieceAt(candidate);
            if (piece != null && piece.color() == byColor && piece.type() == PieceType.KING) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAttackedBySlider(Board board, Position square, Color byColor,
                                               int[][] directions, PieceType primaryType) {
        for (int[] direction : directions) {
            Position current = square;
            while (current.canTranslate(direction[0], direction[1])) {
                current = current.translate(direction[0], direction[1]);
                Piece piece = board.pieceAt(current);
                if (piece == null) {
                    continue;
                }
                if (piece.color() == byColor
                        && (piece.type() == primaryType || piece.type() == PieceType.QUEEN)) {
                    return true;
                }
                break;
            }
        }
        return false;
    }
}
