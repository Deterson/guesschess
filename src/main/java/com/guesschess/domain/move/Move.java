package com.guesschess.domain.move;

import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;

/**
 * Un coup pseudo-legal ou legal. capturedPiece et promotionType sont nullable.
 */
public record Move(
        Position from,
        Position to,
        Piece movedPiece,
        Piece capturedPiece,
        MoveType type,
        PieceType promotionType
) {

    public Move {
        if (from == null || to == null || movedPiece == null || type == null) {
            throw new IllegalArgumentException("from, to, movedPiece and type must not be null");
        }
        if (type == MoveType.PROMOTION && promotionType == null) {
            throw new IllegalArgumentException("promotionType is required for a PROMOTION move");
        }
        if (type != MoveType.PROMOTION && promotionType != null) {
            throw new IllegalArgumentException("promotionType must be null for a non-PROMOTION move");
        }
    }

    public static Move normal(Position from, Position to, Piece movedPiece, Piece capturedPiece) {
        return new Move(from, to, movedPiece, capturedPiece, MoveType.NORMAL, null);
    }

    public static Move doublePawnPush(Position from, Position to, Piece movedPiece) {
        return new Move(from, to, movedPiece, null, MoveType.DOUBLE_PAWN_PUSH, null);
    }

    public static Move castleKingside(Position from, Position to, Piece king) {
        return new Move(from, to, king, null, MoveType.CASTLE_KINGSIDE, null);
    }

    public static Move castleQueenside(Position from, Position to, Piece king) {
        return new Move(from, to, king, null, MoveType.CASTLE_QUEENSIDE, null);
    }

    public static Move enPassant(Position from, Position to, Piece movedPiece, Piece capturedPawn) {
        return new Move(from, to, movedPiece, capturedPawn, MoveType.EN_PASSANT, null);
    }

    public static Move promotion(Position from, Position to, Piece movedPiece, Piece capturedPiece, PieceType promotionType) {
        return new Move(from, to, movedPiece, capturedPiece, MoveType.PROMOTION, promotionType);
    }

    public boolean isCapture() {
        return capturedPiece != null;
    }
}
