package com.guesschess.tournament;

import com.guesschess.application.computer.AgentId;
import com.guesschess.application.computer.ComputerLevel;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Un agent participant au tournoi (etape 22), tel que passe en ligne de commande :
 * "nom@version" seul, "nom@version:easy|medium|hard" (reutilise les presets existants,
 * registre de l'etape 20), ou "nom@version:cle=valeur,..." pour un budget custom (voir
 * TournamentAgentFactory - seuls guessaware@1 et negamax-timed@1 le supportent).
 * raw() sert d'identifiant d'affichage/regroupement dans le rapport - deux configurations
 * du meme agent sont volontairement des concurrents distincts.
 */
public record AgentSpec(AgentId id, ComputerLevel level, Map<String, String> params, String raw) {

    public static AgentSpec parse(String token) {
        String trimmed = token.trim();
        int colon = trimmed.indexOf(':');
        String idPart = colon < 0 ? trimmed : trimmed.substring(0, colon);
        AgentId id = AgentId.parse(idPart);
        ComputerLevel level = null;
        Map<String, String> params = new LinkedHashMap<>();
        if (colon >= 0) {
            for (String part : trimmed.substring(colon + 1).split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                int eq = p.indexOf('=');
                if (eq < 0) {
                    level = ComputerLevel.valueOf(p.toUpperCase(Locale.ROOT));
                } else {
                    params.put(p.substring(0, eq).trim(), p.substring(eq + 1).trim());
                }
            }
        }
        return new AgentSpec(id, level, params, trimmed);
    }

    public String label() {
        return raw;
    }
}
