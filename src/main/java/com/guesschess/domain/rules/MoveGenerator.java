package com.guesschess.domain.rules;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.CastlingRights;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.Piece;
import com.guesschess.domain.piece.PieceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Generation des coups pseudo-legaux (respectent le deplacement des pieces mais peuvent
 * laisser son propre roi en echec) et des coups legaux (filtres par CheckDetector).
 */
public final class MoveGenerator {

    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] KNIGHT_DELTAS = {
            {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    private static final int[][] KING_DELTAS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };
    private static final PieceType[] PROMOTION_TYPES = {
            PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
    };

    private MoveGenerator() {
    }

    public static List<Move> generateLegalMoves(Board board, Color color) {
        List<Move> legalMoves = new ArrayList<>();
        for (Move move : generatePseudoLegalMoves(board, color)) {
            if (leavesOwnKingSafe(board, color, move)) {
                legalMoves.add(move);
            }
        }
        return legalMoves;
    }

    /**
     * Verifie qu'un coup precis est legal, sans construire la liste complete des coups
     * legaux : ne genere les coups pseudo-legaux que pour la piece concernee, puis ne
     * simule qu'un seul coup pour la securite du roi. A utiliser pour valider un coup
     * candidat (ex. Game.makeMove), pas pour lister les options d'un joueur.
     */
    public static boolean isLegalMove(Board board, Color color, Move move) {
        Piece piece = board.pieceAt(move.from());
        if (piece == null || piece.color() != color || !piece.equals(move.movedPiece())) {
            return false;
        }
        if (!pseudoLegalMovesFrom(board, move.from(), piece).contains(move)) {
            return false;
        }
        return leavesOwnKingSafe(board, color, move);
    }

    /**
     * Indique si une couleur a au moins un coup legal, sans construire la liste complete :
     * s'arrete des le premier coup legal trouve. A utiliser pour detecter mat/pat
     * (Game.resolveGameEnd), ou seule la non-vacuite de la liste importe.
     */
    public static boolean hasAnyLegalMove(Board board, Color color) {
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Position from = Position.of(file, rank);
                Piece piece = board.pieceAt(from);
                if (piece == null || piece.color() != color) {
                    continue;
                }
                for (Move move : pseudoLegalMovesFrom(board, from, piece)) {
                    if (leavesOwnKingSafe(board, color, move)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean leavesOwnKingSafe(Board board, Color color, Move move) {
        Board after = board.applyMove(move);
        return !CheckDetector.isInCheck(after, color);
    }

    public static List<Move> generatePseudoLegalMoves(Board board, Color color) {
        List<Move> moves = new ArrayList<>();
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Position from = Position.of(file, rank);
                Piece piece = board.pieceAt(from);
                if (piece == null || piece.color() != color) {
                    continue;
                }
                moves.addAll(pseudoLegalMovesFrom(board, from, piece));
            }
        }
        return moves;
    }

    private static List<Move> pseudoLegalMovesFrom(Board board, Position from, Piece piece) {
        List<Move> moves = new ArrayList<>();
        switch (piece.type()) {
            case PAWN -> generatePawnMoves(board, from, piece, moves);
            case KNIGHT -> generateLeaperMoves(board, from, piece, KNIGHT_DELTAS, moves);
            case BISHOP -> generateSliderMoves(board, from, piece, BISHOP_DIRECTIONS, moves);
            case ROOK -> generateSliderMoves(board, from, piece, ROOK_DIRECTIONS, moves);
            case QUEEN -> {
                generateSliderMoves(board, from, piece, ROOK_DIRECTIONS, moves);
                generateSliderMoves(board, from, piece, BISHOP_DIRECTIONS, moves);
            }
            case KING -> {
                generateLeaperMoves(board, from, piece, KING_DELTAS, moves);
                generateCastlingMoves(board, piece, moves);
            }
        }
        return moves;
    }

    private static void generatePawnMoves(Board board, Position from, Piece pawn, List<Move> moves) {
        Color color = pawn.color();
        int dir = color == Color.WHITE ? 1 : -1;
        int startRank = color == Color.WHITE ? 1 : 6;
        int promotionRank = color == Color.WHITE ? 7 : 0;

        if (from.canTranslate(0, dir)) {
            Position onePush = from.translate(0, dir);
            if (board.isEmpty(onePush)) {
                addPawnAdvance(from, onePush, pawn, null, promotionRank, moves);
                if (from.rank() == startRank && from.canTranslate(0, 2 * dir)) {
                    Position twoPush = from.translate(0, 2 * dir);
                    if (board.isEmpty(twoPush)) {
                        moves.add(Move.doublePawnPush(from, twoPush, pawn));
                    }
                }
            }
        }

        for (int fileOffset : new int[]{-1, 1}) {
            if (!from.canTranslate(fileOffset, dir)) {
                continue;
            }
            Position target = from.translate(fileOffset, dir);
            Piece targetPiece = board.pieceAt(target);
            if (targetPiece != null && targetPiece.color() != color) {
                addPawnAdvance(from, target, pawn, targetPiece, promotionRank, moves);
            } else if (targetPiece == null && target.equals(board.enPassantTarget())) {
                Piece capturedPawn = Piece.of(PieceType.PAWN, color.opposite());
                moves.add(Move.enPassant(from, target, pawn, capturedPawn));
            }
        }
    }

    private static void addPawnAdvance(Position from, Position to, Piece pawn, Piece captured,
                                        int promotionRank, List<Move> moves) {
        if (to.rank() == promotionRank) {
            for (PieceType promotionType : PROMOTION_TYPES) {
                moves.add(Move.promotion(from, to, pawn, captured, promotionType));
            }
        } else {
            moves.add(Move.normal(from, to, pawn, captured));
        }
    }

    private static void generateLeaperMoves(Board board, Position from, Piece piece, int[][] deltas, List<Move> moves) {
        for (int[] delta : deltas) {
            if (!from.canTranslate(delta[0], delta[1])) {
                continue;
            }
            Position to = from.translate(delta[0], delta[1]);
            Piece targetPiece = board.pieceAt(to);
            if (targetPiece == null || targetPiece.color() != piece.color()) {
                moves.add(Move.normal(from, to, piece, targetPiece));
            }
        }
    }

    private static void generateSliderMoves(Board board, Position from, Piece piece, int[][] directions, List<Move> moves) {
        for (int[] direction : directions) {
            Position current = from;
            while (current.canTranslate(direction[0], direction[1])) {
                current = current.translate(direction[0], direction[1]);
                Piece targetPiece = board.pieceAt(current);
                if (targetPiece == null) {
                    moves.add(Move.normal(from, current, piece, null));
                    continue;
                }
                if (targetPiece.color() != piece.color()) {
                    moves.add(Move.normal(from, current, piece, targetPiece));
                }
                break;
            }
        }
    }

    private static void generateCastlingMoves(Board board, Piece king, List<Move> moves) {
        Color color = king.color();
        int rank = color == Color.WHITE ? 0 : 7;
        Position kingHome = Position.of(4, rank);
        if (!king.equals(board.pieceAt(kingHome))) {
            return;
        }
        CastlingRights rights = board.castlingRights();
        Color opponent = color.opposite();
        boolean kingside = color == Color.WHITE ? rights.whiteKingside() : rights.blackKingside();
        boolean queenside = color == Color.WHITE ? rights.whiteQueenside() : rights.blackQueenside();

        if (kingside) {
            Position rookHome = Position.of(7, rank);
            Position f = Position.of(5, rank);
            Position g = Position.of(6, rank);
            Piece rook = Piece.of(PieceType.ROOK, color);
            if (rook.equals(board.pieceAt(rookHome))
                    && board.isEmpty(f) && board.isEmpty(g)
                    && !CheckDetector.isSquareAttacked(board, kingHome, opponent)
                    && !CheckDetector.isSquareAttacked(board, f, opponent)
                    && !CheckDetector.isSquareAttacked(board, g, opponent)) {
                moves.add(Move.castleKingside(kingHome, g, king));
            }
        }
        if (queenside) {
            Position rookHome = Position.of(0, rank);
            Position b = Position.of(1, rank);
            Position c = Position.of(2, rank);
            Position d = Position.of(3, rank);
            Piece rook = Piece.of(PieceType.ROOK, color);
            if (rook.equals(board.pieceAt(rookHome))
                    && board.isEmpty(b) && board.isEmpty(c) && board.isEmpty(d)
                    && !CheckDetector.isSquareAttacked(board, kingHome, opponent)
                    && !CheckDetector.isSquareAttacked(board, d, opponent)
                    && !CheckDetector.isSquareAttacked(board, c, opponent)) {
                moves.add(Move.castleQueenside(kingHome, c, king));
            }
        }
    }
}
