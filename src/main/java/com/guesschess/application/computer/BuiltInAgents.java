package com.guesschess.application.computer;

import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Versions d'agents livrees avec le code, sans dependance Spring (le tournoi de l'etape 22
 * les enregistre a l'identique). Les versions gelees ne changent jamais : minimax@1 est le
 * moteur de l'etape 17 tel quel ; toute evolution = une nouvelle version enregistree ici.
 */
public final class BuiltInAgents {

    public static final AgentId MINIMAX_V1 = new AgentId("minimax", 1);
    public static final AgentId GUESSAWARE_V1 = new AgentId("guessaware", 1);

    private BuiltInAgents() {
    }

    /** Enregistre minimax@1 (et l'alias historique "minimax"). */
    public static AgentRegistry registerMinimaxV1(AgentRegistry registry) {
        Function<RandomGenerator, ChessEngine> engines = MinimaxChessEngine::new;
        return registry
                .register(MINIMAX_V1, level -> new EngineBackedAgent(MINIMAX_V1, level, engines, () -> true))
                .alias("minimax", MINIMAX_V1);
    }

    /** Enregistre guessaware@1 (etape 21), a cote de minimax@1 - jamais le defaut tant que le tournoi (etape 22) ne l a pas valide. */
    public static AgentRegistry registerGuessAwareV1(AgentRegistry registry) {
        return registry.register(GUESSAWARE_V1,
                level -> new GuessAwareAgent(GUESSAWARE_V1, GuessAwareAgent.Config.forLevel(level)));
    }
}
