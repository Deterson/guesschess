package com.guesschess.application;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;

/**
 * Association jeton -> couleur pour une partie, un concept d'application (session).
 * Porte aussi, depuis l'etape 6, le lien couleur -> joueur reel (compte ou identite
 * anonyme) : whitePlayer/blackPlayer restent null tant que la couleur correspondante
 * n'a pas encore agi, puis sont poses une seule fois (voir withPlayerLinked) et ne
 * changent plus jamais - premier arrive, premier lie.
 */
public record GameAccess(GameId gameId, PlayerToken whiteToken, PlayerToken blackToken,
                          PlayerRef whitePlayer, PlayerRef blackPlayer) {

    public GameAccess {
        if (gameId == null || whiteToken == null || blackToken == null) {
            throw new IllegalArgumentException("gameId, whiteToken and blackToken must not be null");
        }
        if (whiteToken.equals(blackToken)) {
            throw new IllegalArgumentException("whiteToken and blackToken must be different");
        }
    }

    public GameAccess(GameId gameId, PlayerToken whiteToken, PlayerToken blackToken) {
        this(gameId, whiteToken, blackToken, null, null);
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

    public PlayerRef playerOf(Color color) {
        return color == Color.WHITE ? whitePlayer : blackPlayer;
    }

    public boolean isFull() {
        return whitePlayer != null && blackPlayer != null;
    }

    /**
     * Lie ref a color si cette couleur n'est pas deja liee, sinon ne fait rien : le
     * lien est immuable une fois pose (voir doc de la classe).
     */
    public GameAccess withPlayerLinked(Color color, PlayerRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("ref must not be null");
        }
        return switch (color) {
            case WHITE -> whitePlayer == null ? new GameAccess(gameId, whiteToken, blackToken, ref, blackPlayer) : this;
            case BLACK -> blackPlayer == null ? new GameAccess(gameId, whiteToken, blackToken, whitePlayer, ref) : this;
        };
    }
}
