package com.guesschess.application;

import com.guesschess.domain.piece.Color;

/**
 * Resultat d'une recuperation d'acces par identite (etape 7) - voir
 * GameLifecycleService.findMyAccess.
 */
public record MyAccess(Color color, PlayerToken token) {
}
