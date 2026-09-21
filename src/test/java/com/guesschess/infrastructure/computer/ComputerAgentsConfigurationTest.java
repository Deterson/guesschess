package com.guesschess.infrastructure.computer;

import com.guesschess.application.computer.AgentProvider;
import com.guesschess.application.computer.BuiltInAgents;
import com.guesschess.application.computer.ComputerLevel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cablage du registre d'agents (etape 20) avec les vraies valeurs par defaut de
 * application.properties (placeholders imbriques compris), sans demarrer toute l'application
 * (ni base de donnees).
 */
class ComputerAgentsConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ComputerAgentsConfiguration.class, StockfishChessEngine.class);

    @Test
    void defaultsToMinimaxV1ForEveryLevel() {
        runner.run(context -> {
            AgentProvider provider = context.getBean(AgentProvider.class);
            for (ComputerLevel level : ComputerLevel.values()) {
                assertEquals(BuiltInAgents.MINIMAX_V1, provider.agentFor(level).id());
            }
        });
    }

    @Test
    void oneLevelCanBeSwitchedWithoutTouchingTheOthers() {
        runner.withPropertyValues("GUESSCHESS_ENGINE_HARD=stockfish@1").run(context -> {
            AgentProvider provider = context.getBean(AgentProvider.class);
            assertEquals("stockfish@1", provider.agentFor(ComputerLevel.HARD).id().toString());
            assertEquals("minimax@1", provider.agentFor(ComputerLevel.EASY).id().toString());
            // pas de STOCKFISH_PATH dans ce contexte : l'agent est bien celui de Stockfish, indisponible
            assertFalse(provider.agentFor(ComputerLevel.HARD).isAvailable());
        });
    }

    @Test
    void guessawareCanBeSelectedForOneLevelAlongsideMinimax() {
        runner.withPropertyValues("GUESSCHESS_ENGINE_HARD=guessaware@1").run(context -> {
            AgentProvider provider = context.getBean(AgentProvider.class);
            assertEquals("guessaware@1", provider.agentFor(ComputerLevel.HARD).id().toString());
            assertEquals("minimax@1", provider.agentFor(ComputerLevel.MEDIUM).id().toString());
            assertTrue(provider.agentFor(ComputerLevel.HARD).isAvailable());
        });
    }

    @Test
    void legacyGuesschessEngineVariableStillSwitchesEveryLevel() {
        runner.withPropertyValues("GUESSCHESS_ENGINE=stockfish").run(context -> {
            AgentProvider provider = context.getBean(AgentProvider.class);
            assertEquals("stockfish@1", provider.agentFor(ComputerLevel.MEDIUM).id().toString());
        });
    }

    @Test
    void anUnknownVersionStopsTheApplicationFromStarting() {
        runner.withPropertyValues("GUESSCHESS_ENGINE_EASY=minimax@99").run(context ->
                assertNotNull(context.getStartupFailure()));
    }
}
