package com.guesschess.application;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;

/**
 * Association jeton -> couleur pour une partie, un concept d'application (session),
 * pas du domaine : rien a voir avec un futur compte joueur (etape 4).
 */
public record GameAccess(GameId gameId, PlayerToken whiteToken, PlayerToken blackToken) {

    public GameAccess {
        if (gameId == null || whiteToken == null || blackToken == null) {
            throw new IllegalArgumentException("gameId, whiteToken and blackToken must not be null");
        }
        if (whiteToken.equals(blackToken)) {
            throw new IllegalArgumentException("whiteToken and blackToken must be different");
        }
    }

    /**
     * @return la couleur associee a token, ou null si token n'appartient pas a cette partie
     */
    public Color colorOf(PlayerToken token) {
        if (whiteToken.equals(token)) {
            return Color.WHITE;
        }
        if (blackToken.equals(token)) {
            return Color.BLACK;
        }
        return null;
    }
}
