package com.guesschess.application;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;

import java.util.Optional;

/**
 * Port : ecrit une fois a la creation de la partie, lu ensuite a chaque action d'un
 * joueur pour retrouver sa couleur a partir de son jeton.
 */
public interface GameAccessRepository {

    void save(GameAccess access);

    Optional<GameAccess> findByToken(PlayerToken token);

    Optional<GameAccess> findByGameId(GameId gameId);

    /**
     * Lie ref a color pour gameId si cette couleur n'est pas deja liee (etape 6) -
     * aucun effet sinon, y compris si ref differe du lien deja pose : le lien est
     * immuable une fois etabli (voir GameAccess.withPlayerLinked).
     *
     * @return le PlayerRef desormais associe a color (celui qui a gagne la course :
     * ref si cet appel vient de poser le lien, ou le lien deja en place sinon) - permet
     * a un appelant (etape 7, acceptation d'invitation) de distinguer un lien reussi
     * d'une couleur deja prise par quelqu'un d'autre.
     */
    PlayerRef linkPlayer(GameId gameId, Color color, PlayerRef ref);
}
