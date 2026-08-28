package com.guesschess.application;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;

/**
 * Resultat d'une tentative d'acceptation d'invitation (etape 7 de la roadmap) - voir
 * GameLifecycleService.joinGame. token est celui de la couleur revendiquee, a utiliser
 * immediatement par l'appelant pour jouer (jamais expose dans une URL - voir
 * PlayerToken).
 */
public record JoinResult(GameId gameId, Color color, PlayerToken token, boolean linkedToRequester) {
}
