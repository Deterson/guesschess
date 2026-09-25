package com.guesschess.tournament;

import com.guesschess.application.computer.AgentId;
import com.guesschess.application.computer.AgentRegistry;
import com.guesschess.application.computer.BuiltInAgents;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.application.computer.EngineBackedAgent;
import com.guesschess.application.computer.GuessAgent;
import com.guesschess.application.computer.GuessAwareAgent;
import com.guesschess.application.computer.NegamaxTimedAgent;
import com.guesschess.infrastructure.computer.StockfishChessEngine;

/**
 * Construit le registre d'agents du tournoi (etape 22) - pur (aucun contexte Spring),
 * comme le reste de application.computer (voir AgentRegistry, BuiltInAgents). stockfish@1
 * est instancie directement (hors Spring) si un chemin est fourni ; sinon isAvailable()
 * renverra false et toute AgentSpec qui le cible echouera a la construction.
 */
public final class TournamentAgentFactory {

    private static final AgentId STOCKFISH_V1 = new AgentId("stockfish", 1);

    private TournamentAgentFactory() {
    }

    public static AgentRegistry defaultRegistry(String stockfishPath) {
        AgentRegistry registry = new AgentRegistry();
        BuiltInAgents.registerMinimaxV1(registry);
        BuiltInAgents.registerMinimaxV2(registry);
        BuiltInAgents.registerMinimaxV3(registry);
        BuiltInAgents.registerGuessAwareV1(registry);
        BuiltInAgents.registerNegamaxTimedV1(registry);
        BuiltInAgents.registerRandomV1(registry);
        StockfishChessEngine stockfish = new StockfishChessEngine(stockfishPath == null ? "" : stockfishPath);
        registry.register(STOCKFISH_V1,
                level -> new EngineBackedAgent(STOCKFISH_V1, level, rng -> stockfish, stockfish::isAvailable));
        registry.alias("stockfish", STOCKFISH_V1);
        return registry;
    }

    /**
     * @throws IllegalArgumentException si params() cible un agent qui ne supporte pas de
     *                                  budget custom, ou si l'agent choisi (stockfish@1
     *                                  sans binaire, par exemple) n'est pas disponible.
     */
    public static GuessAgent create(AgentSpec spec, AgentRegistry registry) {
        GuessAgent agent = buildAgent(spec, registry);
        if (!agent.isAvailable()) {
            throw new IllegalArgumentException("agent unavailable: " + spec.label()
                    + " (stockfish@1 requires --stockfish-path or STOCKFISH_PATH)");
        }
        return agent;
    }

    private static GuessAgent buildAgent(AgentSpec spec, AgentRegistry registry) {
        if (spec.params().isEmpty()) {
            ComputerLevel level = spec.level() != null ? spec.level() : ComputerLevel.MEDIUM;
            return registry.create(spec.id(), level);
        }
        return switch (spec.id().name()) {
            case "guessaware" -> new GuessAwareAgent(spec.id(), new GuessAwareAgent.Config(
                    intParam(spec, "depth", 3), intParam(spec, "guessPlies", 1), longParam(spec, "budgetMillis", 1_000)));
            case "negamax-timed" -> new NegamaxTimedAgent(spec.id(), new NegamaxTimedAgent.Config(
                    intParam(spec, "maxDepth", 5), longParam(spec, "budgetMillis", 1_000)));
            default -> throw new IllegalArgumentException(
                    "agent " + spec.id() + " does not support custom params " + spec.params().keySet()
                            + " - use a level (easy/medium/hard) instead");
        };
    }

    private static int intParam(AgentSpec spec, String key, int fallback) {
        String value = spec.params().get(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static long longParam(AgentSpec spec, String key, long fallback) {
        String value = spec.params().get(key);
        return value == null ? fallback : Long.parseLong(value);
    }
}
