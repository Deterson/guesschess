package com.guesschess.application.computer;

import java.util.HashMap;
import java.util.Map;

/**
 * Registre "nom@version -> fabrique" (etape 20). C'est le seul endroit ou l'on branche sur
 * une version : les algorithmes n'ont jamais de "if version". Pur (aucune dependance
 * Spring), donc utilisable tel quel par le tournoi (etape 22). Les alias ("minimax" ->
 * "minimax@1") ne servent qu'a la compatibilite avec l'ancienne propriete guesschess.engine.
 */
public final class AgentRegistry {

    private final Map<AgentId, AgentFactory> factories = new HashMap<>();
    private final Map<String, AgentId> aliases = new HashMap<>();

    public AgentRegistry register(AgentId id, AgentFactory factory) {
        if (factories.putIfAbsent(id, factory) != null) {
            throw new IllegalStateException("agent already registered (versions are immutable): " + id);
        }
        return this;
    }

    public AgentRegistry alias(String legacyName, AgentId target) {
        aliases.put(legacyName, target);
        return this;
    }

    /** "minimax@1" ou un alias ("minimax"). */
    public AgentId resolve(String spec) {
        AgentId aliased = aliases.get(spec == null ? null : spec.trim());
        return aliased != null ? aliased : AgentId.parse(spec);
    }

    public GuessAgent create(AgentId id, ComputerLevel level) {
        AgentFactory factory = factories.get(id);
        if (factory == null) {
            throw new IllegalArgumentException("unknown agent " + id + ", registered: " + factories.keySet());
        }
        return factory.create(level);
    }

    public boolean isRegistered(AgentId id) {
        return factories.containsKey(id);
    }
}
