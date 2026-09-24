package com.guesschess.tournament;

import com.guesschess.application.computer.AgentRegistry;
import com.guesschess.application.computer.GuessAgent;
import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.GameVariant;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Point d'entree du tournoi entre versions de moteur (etape 22 de la roadmap) - "main"
 * autonome, sans dependance Spring/BD (voir CLAUDE.md), lancable depuis les classes
 * compilees ou le jar Spring Boot repackage (voir README de ce package pour les deux
 * commandes). Deux sous-commandes : "run" joue un tournoi round-robin et ecrit un JSONL,
 * "report" lit un ou plusieurs JSONL et affiche le rapport agrege.
 */
public final class Tournament {

    private Tournament() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        try {
            switch (args[0]) {
                case "run" -> run(parseArgs(args));
                case "report" -> report(parseArgs(args));
                default -> {
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur : " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("""
                Usage :
                  run     --agents <spec>[;<spec>...] [--variant GUESSCHESS|GUESSMATE] [--openings N]
                          [--opening-plies N] [--seed N] [--round-limit N] [--threads N]
                          [--out results.jsonl] [--stockfish-path chemin] [--report]
                  report  --in results1.jsonl[,results2.jsonl...] [--out rapport.txt]

                spec d'agent (les specs sont separees par ";", les parametres a l'interieur d'une spec par ",") :
                  "nom@version" | "nom@version:easy|medium|hard" | "nom@version:cle=valeur,..."
                Exemples (--agents "minimax@1:hard;guessaware@1:depth=4,guessPlies=2,budgetMillis=1500;random@1") :
                  minimax@1:hard
                  guessaware@1:depth=4,guessPlies=2,budgetMillis=1500
                  negamax-timed@1:maxDepth=6,budgetMillis=1200
                  random@1
                  stockfish@1:hard   (necessite --stockfish-path ou STOCKFISH_PATH)
                """);
    }

    private static void run(Map<String, String> opts) {
        List<AgentSpec> agentSpecs = Arrays.stream(require(opts, "agents").split(";"))
                .map(AgentSpec::parse).toList();
        GameVariant variant = GameVariant.valueOf(opts.getOrDefault("variant", "GUESSCHESS").toUpperCase(Locale.ROOT));
        int openingsCount = Integer.parseInt(opts.getOrDefault("openings", "6"));
        int openingPlies = Integer.parseInt(opts.getOrDefault("opening-plies", "4"));
        long seed = Long.parseLong(opts.getOrDefault("seed", "42"));
        int roundLimit = Integer.parseInt(opts.getOrDefault("round-limit", "300"));
        int threads = Integer.parseInt(opts.getOrDefault("threads", "1"));
        Path out = Path.of(opts.getOrDefault("out", "tournament-results.jsonl"));
        String stockfishPath = opts.getOrDefault("stockfish-path", System.getenv("STOCKFISH_PATH"));

        AgentRegistry registry = TournamentAgentFactory.defaultRegistry(stockfishPath);
        List<Board> openings = OpeningPositionGenerator.generate(openingsCount, openingPlies, seed);
        List<RoundRobinScheduler.Fixture> fixtures = RoundRobinScheduler.schedule(agentSpecs, openings);
        String machine = hostname();

        System.err.printf(Locale.ROOT, "%d agents, %d ouvertures, %d parties, variante %s, seed %d%n",
                agentSpecs.size(), openingsCount, fixtures.size(), variant, seed);

        try (JsonlGameWriter writer = new JsonlGameWriter(out)) {
            ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (RoundRobinScheduler.Fixture fixture : fixtures) {
                    futures.add(pool.submit(() -> {
                        GuessAgent white = TournamentAgentFactory.create(fixture.white(), registry);
                        GuessAgent black = TournamentAgentFactory.create(fixture.black(), registry);
                        GameRecord record = HeadlessGameRunner.play(fixture, variant, seed, white, black, roundLimit, machine);
                        writer.write(record);
                        System.err.printf(Locale.ROOT, "[%d/%d] %s vs %s -> %s (%s, %d rounds)%n",
                                fixture.gameIndex() + 1, fixtures.size(), fixture.white().label(), fixture.black().label(),
                                record.resultTag(), record.cause(), record.roundCount());
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                pool.shutdown();
            }
        } catch (Exception e) {
            throw new RuntimeException("tournament run failed", e);
        }

        System.err.println("Resultats ecrits dans " + out.toAbsolutePath());
        if (opts.containsKey("report")) {
            System.out.println(TournamentReport.render(TournamentReport.readAll(List.of(out))));
        }
    }

    private static void report(Map<String, String> opts) {
        List<Path> files = Arrays.stream(require(opts, "in").split(",")).map(Path::of).toList();
        String rendered = TournamentReport.render(TournamentReport.readAll(files));
        System.out.print(rendered);
        if (opts.containsKey("out")) {
            try {
                java.nio.file.Files.writeString(Path.of(opts.get("out")), rendered);
            } catch (java.io.IOException e) {
                throw new RuntimeException("failed to write report to " + opts.get("out"), e);
            }
        }
    }

    private static String require(Map<String, String> opts, String key) {
        String value = opts.get(key);
        if (value == null) {
            throw new IllegalArgumentException("missing required --" + key);
        }
        return value;
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + arg);
            }
            String key = arg.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                opts.put(key, args[++i]);
            } else {
                opts.put(key, "true");
            }
        }
        return opts;
    }
}
