package com.guesschess.application.computer;

/**
 * Quel agent joue quel niveau (etape 20) - resolu par configuration
 * (guesschess.engine.easy/medium/hard, voir RegistryAgentProvider), pour changer de version
 * sans rebuild.
 */
@FunctionalInterface
public interface AgentProvider {
    GuessAgent agentFor(ComputerLevel level);
}
