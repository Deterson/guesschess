package com.guesschess.application.computer;

/**
 * Une version de moteur d'IA (etape 20 de la roadmap), identifiee par un AgentId immuable.
 * Distingue jouer et deviner (deux questions differentes, voir AgentSession) contrairement
 * au port ChessEngine, simple oracle "meilleur coup" reutilise par EngineBackedAgent.
 * Instance sans etat de partie : tout l'etat vit dans la AgentSession.
 */
public interface GuessAgent {

    AgentId id();

    /** Verification rapide (sans recherche) que l'agent est utilisable, voir ChessEngine.isAvailable. */
    boolean isAvailable();

    AgentSession newSession(AgentSessionContext context);
}
