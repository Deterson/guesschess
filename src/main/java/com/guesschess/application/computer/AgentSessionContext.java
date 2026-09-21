package com.guesschess.application.computer;

import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.piece.Color;

import java.util.random.RandomGenerator;

/**
 * Ce qu'un agent sait de la partie qu'il va jouer (etape 20). rng : seule source de hasard
 * autorisee dans un agent - avec une graine fixe, une partie devient rejouable (tournoi,
 * etape 22). label : identifiant lisible de la partie, pour les logs uniquement.
 */
public record AgentSessionContext(Color color, GameVariant variant, String label, RandomGenerator rng) {
}
