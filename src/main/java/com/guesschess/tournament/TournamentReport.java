package com.guesschess.tournament;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Agrege un ou plusieurs fichiers JSONL de resultats (etape 22) en un rapport texte :
 * victoires/nulles/defaites par agent, score avec intervalle de confiance a 95%, un Elo
 * approxime, et les statistiques de devinette par role (taux correct = taux d'annulation,
 * voir RoundResult.guessedCorrectly). Une partie ROUND_LIMIT (round-limite atteinte sans
 * issue) compte comme nulle pour le score - convention courante des outils de tournoi
 * d'echecs pour les parties qui trainent sans jamais se conclure.
 *
 * L'Elo est une approximation deliberement simple (etape 22, v1) : performance logistique
 * a partir du score global contre tout le champ, PAS une vraie estimation conjointe multi-
 * joueurs (Bradley-Terry/BayesElo) - suffisant pour classer les agents, pas pour publier un
 * Elo absolu comparable a une autre liste.
 */
public final class TournamentReport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double Z_95 = 1.959964;

    private TournamentReport() {
    }

    public static List<GameRecord> readAll(List<Path> files) {
        List<GameRecord> games = new ArrayList<>();
        for (Path file : files) {
            try {
                for (String line : Files.readAllLines(file)) {
                    if (!line.isBlank()) {
                        games.add(MAPPER.readValue(line, GameRecord.class));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read " + file, e);
            }
        }
        return games;
    }

    public record AgentStats(String agent, int games, double wins, double draws, double losses,
                              double score, double scoreLow, double scoreHigh, double eloDiff,
                              int guesses, int guessesCorrect, double avgThinkMillis) {
    }

    public static Map<String, AgentStats> aggregate(List<GameRecord> games) {
        Map<String, double[]> tally = new LinkedHashMap<>(); // [games, wins, draws, losses, guesses, guessesCorrect, thinkMillis, rounds]
        for (GameRecord game : games) {
            boolean draw = game.abortedRoundLimit() || game.winner() == null;
            record Side(String agent, boolean isWhite) {
            }
            for (Side side : List.of(new Side(game.whiteAgent(), true), new Side(game.blackAgent(), false))) {
                double[] row = tally.computeIfAbsent(side.agent(), k -> new double[8]);
                row[0]++;
                if (draw) {
                    row[2]++;
                } else if (side.isWhite() == "WHITE".equals(game.winner())) {
                    row[1]++;
                } else {
                    row[3]++;
                }
                row[4] += side.isWhite() ? game.whiteGuesses() : game.blackGuesses();
                row[5] += side.isWhite() ? game.whiteGuessesCorrect() : game.blackGuessesCorrect();
                // Chaque round contribue exactement un appel move()/guess() a cette couleur (voir
                // HeadlessGameRunner) : diviser par le nombre de PARTIES sous-estimerait grossierement
                // le temps de reflexion moyen par coup des qu'une partie dure plus d'un round.
                row[6] += side.isWhite() ? game.whiteThinkMillis() : game.blackThinkMillis();
                row[7] += game.roundCount();
            }
        }
        Map<String, AgentStats> stats = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : tally.entrySet()) {
            double[] row = entry.getValue();
            int n = (int) row[0];
            double score = (row[1] + 0.5 * row[2]) / n;
            double se = Math.sqrt(Math.max(score * (1 - score), 1e-9) / n);
            double p = Math.min(Math.max(score, 1e-3), 1 - 1e-3);
            double eloDiff = 400 * Math.log10(p / (1 - p));
            double avgThinkMillis = row[7] == 0 ? 0 : row[6] / row[7];
            stats.put(entry.getKey(), new AgentStats(entry.getKey(), n, row[1], row[2], row[3],
                    score, Math.max(0, score - Z_95 * se), Math.min(1, score + Z_95 * se), eloDiff,
                    (int) row[4], (int) row[5], avgThinkMillis));
        }
        return stats;
    }

    public static String render(List<GameRecord> games) {
        Map<String, AgentStats> stats = aggregate(games);
        List<AgentStats> ranked = new ArrayList<>(stats.values());
        ranked.sort(Comparator.comparingDouble(AgentStats::score).reversed());

        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Tournoi guesschess - %d parties, %d agents%n%n", games.size(), stats.size()));
        out.append(String.format(Locale.ROOT, "%-45s %6s %6s %6s %6s %8s %20s %8s %10s %8s%n",
                "Agent", "J", "V", "N", "D", "Score%", "IC95%", "Elo+/-", "Devine%", "Ms/coup"));
        for (AgentStats s : ranked) {
            String ci = String.format(Locale.ROOT, "[%.1f%%,%.1f%%]", s.scoreLow() * 100, s.scoreHigh() * 100);
            double guessRate = s.guesses() == 0 ? 0 : 100.0 * s.guessesCorrect() / s.guesses();
            out.append(String.format(Locale.ROOT, "%-45s %6d %6.1f %6.1f %6.1f %8.1f %20s %+8.0f %9.1f %8.0f%n",
                    s.agent(), s.games(), s.wins(), s.draws(), s.losses(), s.score() * 100, ci, s.eloDiff(),
                    guessRate, s.avgThinkMillis()));
        }
        out.append(String.format(Locale.ROOT,
                "%nDevine%% = taux de devinettes correctes de l'agent quand il devinait (= taux d'annulation du coup adverse).%n"));
        out.append(String.format(Locale.ROOT,
                "Elo+/- = performance logistique approximee a partir du score global (pas un Bradley-Terry multi-joueurs) ; ancre a 0 = 50%% de score.%n"));

        Map<String, int[]> causeCounts = new LinkedHashMap<>();
        for (GameRecord game : games) {
            String cause = game.cause() == null ? "ONGOING" : game.cause();
            causeCounts.computeIfAbsent(cause, k -> new int[1])[0]++;
        }
        out.append(String.format(Locale.ROOT, "%nCauses de fin de partie :%n"));
        causeCounts.forEach((cause, count) -> out.append(String.format(Locale.ROOT, "  %-30s %d%n", cause, count[0])));

        return out.toString();
    }
}
