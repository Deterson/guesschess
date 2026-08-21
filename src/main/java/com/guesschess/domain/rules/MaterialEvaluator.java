package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Detection des cas de materiel insuffisant pour mater (nulle forcee).
 * Couvre : roi contre roi, roi+cavalier contre roi, roi+fou contre roi,
 * roi+fou contre roi+fou avec fous de meme couleur de case.
 */
public final class MaterialEvaluator {

    private MaterialEvaluator() {
    }

    public static boolean isInsufficientMaterial(Board board) {
        List<Piece> nonKingPieces = new ArrayList<>();
        List<Position> nonKingPositions = new ArrayList<>();
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Position pos = Position.of(file, rank);
                Piece piece = board.pieceAt(pos);
                if (piece == null || piece.type() == PieceType.KING) {
                    continue;
                }
                if (piece.type() == PieceType.PAWN || piece.type() == PieceType.ROOK || piece.type() == PieceType.QUEEN) {
                    return false;
                }
                nonKingPieces.add(piece);
                nonKingPositions.add(pos);
            }
        }

        if (nonKingPieces.isEmpty()) {
            return true;
        }
        if (nonKingPieces.size() == 1) {
            return true;
        }
        if (nonKingPieces.size() == 2
                && nonKingPieces.get(0).type() == PieceType.BISHOP
                && nonKingPieces.get(1).type() == PieceType.BISHOP) {
            return squareColor(nonKingPositions.get(0)) == squareColor(nonKingPositions.get(1));
        }
        return false;
    }

    private static int squareColor(Position position) {
        return (position.file() + position.rank()) % 2;
    }
}
