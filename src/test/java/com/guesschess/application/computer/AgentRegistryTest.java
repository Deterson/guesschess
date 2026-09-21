package com.guesschess.application.computer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRegistryTest {

    @Test
    void parsesNameAtVersion() {
        assertEquals(new AgentId("minimax", 1), AgentId.parse("minimax@1"));
        assertEquals("guessaware@12", new AgentId("guessaware", 12).toString());
    }

    @Test
    void rejectsMalformedIds() {
        assertThrows(IllegalArgumentException.class, () -> AgentId.parse("minimax"));
        assertThrows(IllegalArgumentException.class, () -> AgentId.parse("minimax@"));
        assertThrows(IllegalArgumentException.class, () -> AgentId.parse("@1"));
        assertThrows(IllegalArgumentException.class, () -> AgentId.parse("minimax@x"));
        assertThrows(IllegalArgumentException.class, () -> AgentId.parse("minimax@0"));
    }

    @Test
    void resolvesLegacyAliasesAndExplicitVersions() {
        AgentRegistry registry = BuiltInAgents.registerMinimaxV1(new AgentRegistry());

        assertEquals(BuiltInAgents.MINIMAX_V1, registry.resolve("minimax"));
        assertEquals(BuiltInAgents.MINIMAX_V1, registry.resolve("minimax@1"));
    }

    @Test
    void aVersionCanNeverBeRegisteredTwice() {
        AgentRegistry registry = BuiltInAgents.registerMinimaxV1(new AgentRegistry());

        assertThrows(IllegalStateException.class, () -> BuiltInAgents.registerMinimaxV1(registry));
    }

    @Test
    void createsTheAgentOfTheRequestedVersion() {
        AgentRegistry registry = BuiltInAgents.registerMinimaxV1(new AgentRegistry());

        GuessAgent agent = registry.create(BuiltInAgents.MINIMAX_V1, ComputerLevel.HARD);

        assertEquals(BuiltInAgents.MINIMAX_V1, agent.id());
        assertTrue(agent.isAvailable());
    }

    @Test
    void anUnknownVersionFailsFastAtProviderConstruction() {
        AgentRegistry registry = BuiltInAgents.registerMinimaxV1(new AgentRegistry());
        Map<ComputerLevel, String> specs = Map.of(
                ComputerLevel.EASY, "minimax@1",
                ComputerLevel.MEDIUM, "minimax@1",
                ComputerLevel.HARD, "minimax@2");

        assertThrows(IllegalArgumentException.class, () -> new RegistryAgentProvider(registry, specs));
    }

    @Test
    void providerPicksTheConfiguredVersionPerLevel() {
        AgentRegistry registry = BuiltInAgents.registerMinimaxV1(new AgentRegistry());

        RegistryAgentProvider provider = new RegistryAgentProvider(registry, Map.of(
                ComputerLevel.EASY, "minimax",
                ComputerLevel.MEDIUM, "minimax@1",
                ComputerLevel.HARD, "minimax@1"));

        assertEquals(BuiltInAgents.MINIMAX_V1, provider.agentFor(ComputerLevel.EASY).id());
    }
}
