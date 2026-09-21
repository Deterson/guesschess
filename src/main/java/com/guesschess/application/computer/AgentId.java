package com.guesschess.application.computer;

/**
 * Identifiant d'une version de moteur, "nom@version" (etape 20 de la roadmap). Une version
 * publiee est immuable : un changement de comportement = une nouvelle version, jamais une
 * modification de l'ancienne, pour qu'elle reste un adversaire de reference fiable (tournoi,
 * etape 22).
 */
public record AgentId(String name, int version) {

    public AgentId {
        if (name == null || name.isBlank() || name.contains("@")) {
            throw new IllegalArgumentException("invalid agent name: " + name);
        }
        if (version < 1) {
            throw new IllegalArgumentException("agent version must be >= 1, got " + version);
        }
    }

    /** @throws IllegalArgumentException si spec n'est pas de la forme "nom@N" */
    public static AgentId parse(String spec) {
        int at = spec == null ? -1 : spec.lastIndexOf('@');
        if (at <= 0 || at == spec.length() - 1) {
            throw new IllegalArgumentException("agent id must look like name@version, got: " + spec);
        }
        try {
            return new AgentId(spec.substring(0, at).trim(), Integer.parseInt(spec.substring(at + 1).trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("agent version must be an integer, got: " + spec, e);
        }
    }

    @Override
    public String toString() {
        return name + "@" + version;
    }
}
