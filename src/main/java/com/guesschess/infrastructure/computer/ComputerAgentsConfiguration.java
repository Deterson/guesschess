package com.guesschess.infrastructure.computer;

import com.guesschess.application.computer.AgentId;
import com.guesschess.application.computer.AgentProvider;
import com.guesschess.application.computer.AgentRegistry;
import com.guesschess.application.computer.BuiltInAgents;
import com.guesschess.application.computer.ComputerLevel;
import com.guesschess.application.computer.EngineBackedAgent;
import com.guesschess.application.computer.RegistryAgentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Cablage Spring du registre de versions de moteur (etape 20) : enregistre les versions
 * livrees (minimax@1, stockfish@1) et resout, par niveau, celle configuree
 * (guesschess.engine.easy/medium/hard, voir application.properties). Une version inconnue
 * fait echouer le demarrage. Ajouter une version = l'enregistrer ici (ou dans BuiltInAgents
 * si elle n'a aucune dependance d'infrastructure), sans toucher aux algorithmes existants.
 */
@Configuration
class ComputerAgentsConfiguration {

    private static final AgentId STOCKFISH_V1 = new AgentId("stockfish", 1);

    @Bean
    AgentRegistry agentRegistry(StockfishChessEngine stockfish) {
        AgentRegistry registry = new AgentRegistry();
        BuiltInAgents.registerMinimaxV1(registry);
        BuiltInAgents.registerGuessAwareV1(registry);
        registry.register(STOCKFISH_V1,
                level -> new EngineBackedAgent(STOCKFISH_V1, level, rng -> stockfish, stockfish::isAvailable));
        registry.alias("stockfish", STOCKFISH_V1);
        return registry;
    }

    @Bean
    AgentProvider agentProvider(AgentRegistry registry,
                                @Value("${guesschess.engine.easy}") String easy,
                                @Value("${guesschess.engine.medium}") String medium,
                                @Value("${guesschess.engine.hard}") String hard) {
        return new RegistryAgentProvider(registry, Map.of(
                ComputerLevel.EASY, easy,
                ComputerLevel.MEDIUM, medium,
                ComputerLevel.HARD, hard));
    }
}
