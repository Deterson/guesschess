package com.guesschess.application;

import com.guesschess.domain.game.GameId;

/**
 * Leve quand on tente de rejoindre une partie dont les deux couleurs sont deja
 * revendiquees (etape 7, lien sans token) - le createur revendique toujours la sienne
 * a la creation, donc ce cas signale soit une partie deja complete a deux joueurs,
 * soit une course perdue contre un autre visiteur qui vient de revendiquer le dernier
 * siege libre.
 */
public class NoOpenColorException extends RuntimeException {

    public NoOpenColorException(GameId gameId) {
        super("no open color to join for game: " + gameId);
    }
}
