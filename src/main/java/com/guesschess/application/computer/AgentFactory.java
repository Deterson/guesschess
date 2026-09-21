package com.guesschess.application.computer;

/** Construit l'agent d'une version donnee pour un niveau (etape 20) - voir AgentRegistry. */
@FunctionalInterface
public interface AgentFactory {
    GuessAgent create(ComputerLevel level);
}
