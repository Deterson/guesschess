package com.guesschess.domain.board;

import com.guesschess.domain.move.Move;
import com.guesschess.domain.move.MoveType;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;

import java.util.Arrays;

/**
 * Etat immuable d'une position d'echecs : pieces, trait, droits de roque,
 * case en passant, compteurs de demi-coups/coups. Ne connait pas les regles
 * de legalite (voir domain.rules) ni l'historique de partie (voir domain.game.Game).
 */
public final class Board {

    private final Piece[] squares;
    private final Color sideToMove;
    private final CastlingRights castlingRights;
    private final Position enPassantTarget;
    private final int halfmoveClock;
    private final int fullmoveNumber;

    private Board(Piece[] squares, Color sideToMove, CastlingRights castlingRights,
                  Position enPassantTarget, int halfmoveClock, int fullmoveNumber) {
        this.squares = squares;
        this.sideToMove = sideToMove;
        this.castlingRights = castlingRights;
        this.enPassantTarget = enPassantTarget;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    public static Board initial() {
        Piece[] squares = new Piece[64];
        PieceType[] backRank = {
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        };
        for (int file = 0; file < 8; file++) {
            squares[index(file, 0)] = Piece.of(backRank[file], Color.WHITE);
            squares[index(file, 1)] = Piece.of(PieceType.PAWN, Color.WHITE);
            squares[index(file, 6)] = Piece.of(PieceType.PAWN, Color.BLACK);
            squares[index(file, 7)] = Piece.of(backRank[file], Color.BLACK);
        }
        return new Board(squares, Color.WHITE, CastlingRights.initial(), null, 0, 1);
    }

    public static Board empty() {
        return new Board(new Piece[64], Color.WHITE, CastlingRights.none(), null, 0, 1);
    }

    private static int index(int file, int rank) {
        return rank * 8 + file;
    }

    private static int index(Position position) {
        return index(position.file(), position.rank());
    }

    public Piece pieceAt(Position position) {
        return squares[index(position)];
    }

    public boolean isEmpty(Position position) {
        return pieceAt(position) == null;
    }

    public Color sideToMove() {
        return sideToMove;
    }

    public CastlingRights castlingRights() {
        return castlingRights;
    }

    public Position enPassantTarget() {
        return enPassantTarget;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public Board withPiece(Position position, Piece piece) {
        Piece[] newSquares = squares.clone();
        newSquares[index(position)] = piece;
        return new Board(newSquares, sideToMove, castlingRights, enPassantTarget, halfmoveClock, fullmoveNumber);
    }

    public Board withSideToMove(Color color) {
        return new Board(squares.clone(), color, castlingRights, enPassantTarget, halfmoveClock, fullmoveNumber);
    }

    public Board withCastlingRights(CastlingRights rights) {
        return new Board(squares.clone(), sideToMove, rights, enPassantTarget, halfmoveClock, fullmoveNumber);
    }

    /**
     * Applique un coup structurellement valide (deja verifie pseudo-legal) et retourne
     * le nouvel etat. Ne verifie pas la legalite (echec au roi) : c'est le role du
     * generateur de coups.
     */
    public Board applyMove(Move move) {
        Piece[] newSquares = squares.clone();
        Position from = move.from();
        Position to = move.to();
        Piece moved = move.movedPiece();

        newSquares[index(from)] = null;

        Piece placed = move.type() == MoveType.PROMOTION
                ? Piece.of(move.promotionType(), moved.color())
                : moved;
        newSquares[index(to)] = placed;

        if (move.type() == MoveType.EN_PASSANT) {
            Position capturedPawnSquare = Position.of(to.file(), from.rank());
            newSquares[index(capturedPawnSquare)] = null;
        } else if (move.type() == MoveType.CASTLE_KINGSIDE) {
            int rank = from.rank();
            Position rookFrom = Position.of(7, rank);
            Position rookTo = Position.of(5, rank);
            newSquares[index(rookTo)] = newSquares[index(rookFrom)];
            newSquares[index(rookFrom)] = null;
        } else if (move.type() == MoveType.CASTLE_QUEENSIDE) {
            int rank = from.rank();
            Position rookFrom = Position.of(0, rank);
            Position rookTo = Position.of(3, rank);
            newSquares[index(rookTo)] = newSquares[index(rookFrom)];
            newSquares[index(rookFrom)] = null;
        }

        CastlingRights newRights = updatedCastlingRights(from, to, moved);
        Position newEnPassantTarget = move.type() == MoveType.DOUBLE_PAWN_PUSH
                ? Position.of(from.file(), (from.rank() + to.rank()) / 2)
                : null;
        boolean resetClock = moved.type() == PieceType.PAWN || move.isCapture();
        int newHalfmoveClock = resetClock ? 0 : halfmoveClock + 1;
        int newFullmoveNumber = sideToMove == Color.BLACK ? fullmoveNumber + 1 : fullmoveNumber;
        Color newSideToMove = sideToMove.opposite();

        return new Board(newSquares, newSideToMove, newRights, newEnPassantTarget, newHalfmoveClock, newFullmoveNumber);
    }

    private CastlingRights updatedCastlingRights(Position from, Position to, Piece moved) {
        boolean whiteKingside = castlingRights.whiteKingside();
        boolean whiteQueenside = castlingRights.whiteQueenside();
        boolean blackKingside = castlingRights.blackKingside();
        boolean blackQueenside = castlingRights.blackQueenside();

        if (moved.type() == PieceType.KING) {
            if (moved.color() == Color.WHITE) {
                whiteKingside = false;
                whiteQueenside = false;
            } else {
                blackKingside = false;
                blackQueenside = false;
            }
        }

        whiteQueenside &= !touches(from, to, 0, 0);
        whiteKingside &= !touches(from, to, 7, 0);
        blackQueenside &= !touches(from, to, 0, 7);
        blackKingside &= !touches(from, to, 7, 7);

        return new CastlingRights(whiteKingside, whiteQueenside, blackKingside, blackQueenside);
    }

    private static boolean touches(Position from, Position to, int file, int rank) {
        return (from.file() == file && from.rank() == rank) || (to.file() == file && to.rank() == rank);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Board other)) {
            return false;
        }
        return sideToMove == other.sideToMove
                && castlingRights.equals(other.castlingRights)
                && java.util.Objects.equals(enPassantTarget, other.enPassantTarget)
                && Arrays.equals(squares, other.squares);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(squares);
        result = 31 * result + sideToMove.hashCode();
        result = 31 * result + castlingRights.hashCode();
        result = 31 * result + java.util.Objects.hashCode(enPassantTarget);
        return result;
    }

    /**
     * Egalite de position au sens des echecs (pour la regle de repetition) : pieces,
     * trait, droits de roque et case en passant. Alias de equals(), qui ignore deja
     * volontairement halfmoveClock/fullmoveNumber pour cette raison.
     */
    public boolean isSamePosition(Board other) {
        return equals(other);
    }
}
