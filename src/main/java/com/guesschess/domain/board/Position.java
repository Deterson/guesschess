package com.guesschess.domain.board;

/**
 * Case du plateau. file/rank sont des index 0-7 (file 0 = colonne a, rank 0 = rangee 1).
 */
public record Position(int file, int rank) {

    public Position {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException("file and rank must be in [0,7], got file=" + file + " rank=" + rank);
        }
    }

    public static Position of(int file, int rank) {
        return new Position(file, rank);
    }

    public static boolean isValid(int file, int rank) {
        return file >= 0 && file <= 7 && rank >= 0 && rank <= 7;
    }

    public static Position fromAlgebraic(String algebraic) {
        if (algebraic == null || algebraic.length() != 2) {
            throw new IllegalArgumentException("invalid algebraic square: " + algebraic);
        }
        int file = algebraic.charAt(0) - 'a';
        int rank = algebraic.charAt(1) - '1';
        return new Position(file, rank);
    }

    public String toAlgebraic() {
        char fileChar = (char) ('a' + file);
        char rankChar = (char) ('1' + rank);
        return "" + fileChar + rankChar;
    }

    public Position translate(int deltaFile, int deltaRank) {
        return new Position(file + deltaFile, rank + deltaRank);
    }

    public boolean canTranslate(int deltaFile, int deltaRank) {
        return isValid(file + deltaFile, rank + deltaRank);
    }

    @Override
    public String toString() {
        return toAlgebraic();
    }
}
