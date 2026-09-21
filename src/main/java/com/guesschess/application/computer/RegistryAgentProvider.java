package com.guesschess.application.computer;

import java.util.EnumMap;
import java.util.Map;

/**
 * AgentProvider fige au demarrage : une version d'agent par niveau, echec immediat si une
 * version configuree n'existe pas (plutot qu'a la premiere partie contre l'ordinateur).
 */
public final class RegistryAgentProvider implements AgentProvider {

    private final Map<ComputerLevel, GuessAgent> agents = new EnumMap<>(ComputerLevel.class);

    public RegistryAgentProvider(AgentRegistry registry, Map<ComputerLevel, String> specByLevel) {
        for (ComputerLevel level : ComputerLevel.values()) {
            String spec = specByLevel.get(level);
            if (spec == null) {
                throw new IllegalArgumentException("no agent configured for level " + level);
            }
            agents.put(level, registry.create(registry.resolve(spec), level));
        }
    }

    @Override
    public GuessAgent agentFor(ComputerLevel level) {
        return agents.get(level);
    }
}
