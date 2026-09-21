package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;

import java.util.List;

/**
 * Un agent en train de jouer UNE partie (etape 20) : porte l'etat propre a cette partie
 * (memoire des coups annules, derniere devinette...), que le service Spring n'a plus a
 * connaitre. Jamais partage entre parties ; utilise sequentiellement (un round a la fois).
 */
public interface AgentSession {

    /**
     * Notifie qu'un round vient de se resoudre, avant le round suivant. lastRound est null
     * avant tout round resolu (premier round de la partie).
     */
    void onRoundResolved(RoundResult lastRound);

    /** Coup reel a jouer : l'agent est le joueur au trait, legalMoves ses coups legaux. */
    Move move(Board board, List<Move> legalMoves);

    /** Devinette : l'agent n'est pas au trait, opponentLegalMoves sont les coups legaux de l'adversaire. */
    Move guess(Board board, List<Move> opponentLegalMoves);
}
