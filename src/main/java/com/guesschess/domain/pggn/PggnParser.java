package com.guesschess.domain.pggn;

import com.guesschess.domain.piece.Color;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parseur PGGN (etape 10 de la roadmap) : extraction simple d'un texte .pggn vers un
 * PggnGame, sans revalidation contre le moteur de regles (pas de reconstruction de
 * Board/Game). Un fichier .pggn reimporte n'est donc pas une source de verite fiable -
 * seulement une structure de donnees passive, utile pour de l'affichage ou de
 * l'inspection.
 */
public final class PggnParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^\\[(\\S+)\\s+\"([^\"]*)\"]$");
    private static final Pattern MOVE_NUMBER_PATTERN = Pattern.compile("^(\\d+)\\.$");
    private static final Pattern PLY_PATTERN =
            Pattern.compile("^(?<real>[^()\\s]*)(?:\\((?<guess>[^()]+)\\))?(?<suffix>[+#]?)$");

    private PggnParser() {
    }

    public static PggnGame parse(String text) {
        Map<String, String> tags = new LinkedHashMap<>();
        StringBuilder movetext = new StringBuilder();
        boolean inMovetext = false;

        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (!inMovetext) {
                if (line.isEmpty()) {
                    continue;
                }
                Matcher header = HEADER_PATTERN.matcher(line);
                if (header.matches()) {
                    tags.put(header.group(1), header.group(2));
                    continue;
                }
                inMovetext = true;
            }
            movetext.append(line).append(' ');
        }

        return new PggnGame(tags, parsePlies(movetext.toString()));
    }

    private static List<PggnPly> parsePlies(String movetext) {
        String trimmed = movetext.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] tokens = trimmed.split("\\s+");
        List<PggnPly> plies = new ArrayList<>();
        int i = 0;
        while (i < tokens.length) {
            Matcher moveNumberMatcher = MOVE_NUMBER_PATTERN.matcher(tokens[i]);
            if (!moveNumberMatcher.matches()) {
                throw new IllegalArgumentException("expected a move number token (e.g. \"12.\"), got: " + tokens[i]);
            }
            int moveNumber = Integer.parseInt(moveNumberMatcher.group(1));
            i++;
            if (i >= tokens.length) {
                throw new IllegalArgumentException("move number " + moveNumber + " has no ply following it");
            }

            plies.add(parsePly(moveNumber, Color.WHITE, tokens[i]));
            i++;

            if (i < tokens.length && !MOVE_NUMBER_PATTERN.matcher(tokens[i]).matches()) {
                plies.add(parsePly(moveNumber, Color.BLACK, tokens[i]));
                i++;
            }
        }
        return plies;
    }

    private static PggnPly parsePly(int moveNumber, Color mover, String token) {
        Matcher ply = PLY_PATTERN.matcher(token);
        if (!ply.matches()) {
            throw new IllegalArgumentException("malformed ply token: " + token);
        }
        String real = ply.group("real");
        String guess = ply.group("guess");
        String suffix = ply.group("suffix");

        String realSan = (real == null || real.isEmpty()) ? null : real;
        String guessedSan = guess == null ? null : guess + suffix;
        return new PggnPly(moveNumber, mover, realSan, guessedSan);
    }
}
