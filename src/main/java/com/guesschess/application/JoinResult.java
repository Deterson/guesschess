package com.guesschess.application;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;

/**
 * Resultat d'une tentative d'acceptation d'invitation (etape 7 de la roadmap) - voir
 * GameLifecycleService.joinGame.
 */
public record JoinResult(GameId gameId, Color color, boolean linkedToRequester) {
}
