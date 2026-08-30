package com.guesschess.domain.notation;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.board.Position;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.move.MoveType;
import com.guesschess.domain.piece.Color;
import com.guesschess.domain.piece.PieceType;
import com.guesschess.domain.rules.CheckDetector;
import com.guesschess.domain.rules.MoveGenerator;

import java.util.List;

/**
 * Notation algebrique standard (SAN) d'un coup reellement joue. Pur (pas de
 * dependance Spring), utilise par le websocket (liste de coups en direct) et par le
 * PGGN (etape 10 de la roadmap). Ne s'applique qu'a un coup effectivement applique au
 * plateau : une devinette non jouee n'a pas de suffixe +/# calculable de la meme facon
 * (voir CLAUDE.md, section PGGN), donc n'est pas le role de cette classe.
 */
public final class SanGenerator {

    private SanGenerator() {
    }

    /**
     * @param before plateau juste avant le coup (desambiguisation)
     * @param move    le coup joue
     * @param after   plateau juste apres le coup (suffixe echec/mat)
     */
    public static String toSan(Board before, Move move, Board after) {
        return toSanCore(before, move) + checkSuffix(after, move);
    }

    /**
     * SAN sans le suffixe +/#, pour les cas ou celui-ci n'est pas calculable de la
     * meme facon (PGGN, etape 10 : coup devine jamais suffixe, sauf exception
     * Guessmate suffixee "#" a la main par l'appelant - voir PggnWriter) ou pas
     * pertinent (coup non joue, pas de plateau "apres" reel a tester).
     */
    public static String toSanCore(Board before, Move move) {
        if (move.type() == MoveType.CASTLE_KINGSIDE) {
            return "O-O";
        }
        if (move.type() == MoveType.CASTLE_QUEENSIDE) {
            return "O-O-O";
        }

        StringBuilder san = new StringBuilder();
        PieceType type = move.movedPiece().type();
        boolean capture = move.isCapture();

        if (type == PieceType.PAWN) {
            if (capture) {
                san.append(fileChar(move.from()));
            }
        } else {
            san.append(pieceLetter(type));
            san.append(disambiguation(before, move));
        }
        if (capture) {
            san.append('x');
        }
        san.append(move.to().toAlgebraic());
        if (move.type() == MoveType.PROMOTION) {
            san.append('=').append(pieceLetter(move.promotionType()));
        }
        return san.toString();
    }

    private static String checkSuffix(Board after, Move move) {
        Color opponent = move.movedPiece().color().opposite();
        if (!CheckDetector.isInCheck(after, opponent)) {
            return "";
        }
        return MoveGenerator.hasAnyLegalMove(after, opponent) ? "+" : "#";
    }

    private static String disambiguation(Board before, Move move) {
        PieceType type = move.movedPiece().type();
        Color color = move.movedPiece().color();
        List<Move> candidates = MoveGenerator.generateLegalMoves(before, color).stream()
                .filter(candidate -> candidate.movedPiece().type() == type)
                .filter(candidate -> candidate.to().equals(move.to()))
                .filter(candidate -> !candidate.from().equals(move.from()))
                .toList();
        if (candidates.isEmpty()) {
            return "";
        }
        boolean sameFile = candidates.stream().anyMatch(candidate -> candidate.from().file() == move.from().file());
        boolean sameRank = candidates.stream().anyMatch(candidate -> candidate.from().rank() == move.from().rank());
        if (!sameFile) {
            return String.valueOf(fileChar(move.from()));
        }
        if (!sameRank) {
            return String.valueOf(rankChar(move.from()));
        }
        return move.from().toAlgebraic();
    }

    private static char fileChar(Position position) {
        return (char) ('a' + position.file());
    }

    private static char rankChar(Position position) {
        return (char) ('1' + position.rank());
    }

    private static char pieceLetter(PieceType type) {
        return switch (type) {
            case KNIGHT -> 'N';
            case BISHOP -> 'B';
            case ROOK -> 'R';
            case QUEEN -> 'Q';
            case KING -> 'K';
            case PAWN -> throw new IllegalArgumentException("pawn has no SAN piece letter");
        };
    }
}
