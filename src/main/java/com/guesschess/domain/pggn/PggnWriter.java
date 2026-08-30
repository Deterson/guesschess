package com.guesschess.domain.pggn;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameResult;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;
import com.guesschess.domain.notation.SanGenerator;
import com.guesschess.domain.piece.Color;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialisation d'un Game en notation PGGN (etape 10 de la roadmap) : notation
 * inspiree du PGN, avec le coup devine entre parentheses juste apres le coup reel
 * (ex. "1. e4(e3) e5(Nc6)"). Voir CLAUDE.md pour la specification complete des regles
 * d'affichage (round annule, absence de devinette, suffixes +/#, cas Guessmate).
 */
public final class PggnWriter {

    private PggnWriter() {
    }

    public static String write(Game game) {
        return write(game, Map.of());
    }

    /**
     * @param headerOverrides valeurs a utiliser pour Event/Date/White/Black plutot
     *                        que "?" (par defaut) - ces informations ne font pas
     *                        partie de l'agregat Game (domaine pur) et doivent etre
     *                        fournies par l'appelant (application/infrastructure)
     *                        quand elles sont disponibles.
     */
    public static String write(Game game, Map<String, String> headerOverrides) {
        return render(toPggnGame(game, headerOverrides));
    }

    public static PggnGame toPggnGame(Game game, Map<String, String> headerOverrides) {
        List<Game.RoundContext> contexts = game.roundHistoryWithPositions();
        List<PggnPly> plies = new ArrayList<>(contexts.size());
        for (int i = 0; i < contexts.size(); i++) {
            plies.add(toPly((i / 2) + 1, contexts.get(i)));
        }
        return new PggnGame(tags(game, headerOverrides), plies);
    }

    public static String render(PggnGame pggn) {
        StringBuilder text = new StringBuilder();
        pggn.tags().forEach((key, value) -> text.append('[').append(key).append(" \"").append(value).append("\"]\n"));
        text.append('\n');

        List<PggnPly> plies = pggn.plies();
        int i = 0;
        while (i < plies.size()) {
            PggnPly whitePly = plies.get(i);
            text.append(whitePly.moveNumber()).append(". ").append(renderPly(whitePly));
            i++;
            if (i < plies.size() && plies.get(i).moveNumber() == whitePly.moveNumber()) {
                text.append(' ').append(renderPly(plies.get(i)));
                i++;
            }
            if (i < plies.size()) {
                text.append(' ');
            }
        }
        return text.toString();
    }

    private static String renderPly(PggnPly ply) {
        if (ply.guessedSan() == null) {
            return ply.realSan();
        }
        if (ply.realSan() == null) {
            // Round annule. guessedSan ne porte de suffixe que dans le seul cas terminal
            // Guessmate (voir toPly) - et ce "#" s'affiche apres la parenthese fermante,
            // pas dedans (CLAUDE.md : "16. (Ke2)#"), contrairement au suffixe d'un coup
            // reellement joue qui fait partie integrante de son propre SAN.
            if (ply.guessedSan().endsWith("#")) {
                String core = ply.guessedSan().substring(0, ply.guessedSan().length() - 1);
                return "(" + core + ")#";
            }
            return "(" + ply.guessedSan() + ")";
        }
        return ply.realSan() + "(" + ply.guessedSan() + ")";
    }

    private static PggnPly toPly(int moveNumber, Game.RoundContext context) {
        RoundResult round = context.round();
        Move guessedMove = round.guessedMove();

        String realSan = round.movePlayed()
                ? SanGenerator.toSan(context.boardBefore(), round.actualMove(), context.boardAfter())
                : null;

        String guessedSan = null;
        if (guessedMove != null) {
            guessedSan = SanGenerator.toSanCore(context.boardBefore(), guessedMove);
            // round annule (movePlayed=false) sans entree "apres" : seul cas possible est le
            // round terminal Guessmate (roi captura via devinette correcte en echec) - voir
            // Game.RoundContext et Game.resolveRound. Seule exception a la regle "jamais de
            // suffixe sur une devinette" (CLAUDE.md, section PGGN).
            if (!round.movePlayed() && context.boardAfter() == null) {
                guessedSan = guessedSan + "#";
            }
        }

        return new PggnPly(moveNumber, round.mover(), realSan, guessedSan);
    }

    private static Map<String, String> tags(Game game, Map<String, String> overrides) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", overrides.getOrDefault("Event", "?"));
        tags.put("Date", overrides.getOrDefault("Date", "?"));
        tags.put("White", overrides.getOrDefault("White", "?"));
        tags.put("Black", overrides.getOrDefault("Black", "?"));
        tags.put("Variant", game.variant().name());
        tags.put("Result", resultTag(game.result()));
        tags.put("Termination", game.result() == null ? "?" : game.result().cause().name());
        return tags;
    }

    private static String resultTag(GameResult result) {
        if (result == null) {
            return "*";
        }
        if (result.isDraw()) {
            return "1/2-1/2";
        }
        return result.winner() == Color.WHITE ? "1-0" : "0-1";
    }
}
