package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.move.Move;

import java.util.List;

/**
 * Agent "random@1" (etape 22, tournoi) : choisit un coup uniformement au hasard parmi
 * les coups legaux, aussi bien pour jouer que pour deviner - aucune recherche, aucune
 * memoire, aucun traitement particulier de la capture de roi (voir EngineBackedAgent
 * pour ce que "gratuit tant que non force" signifie ailleurs) : un adversaire de
 * reference minimal pour mesurer la force des autres moteurs, pas un joueur competent.
 */
public final class RandomAgent implements GuessAgent {

    private final AgentId id;

    public RandomAgent(AgentId id) {
        this.id = id;
    }

    @Override
    public AgentId id() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AgentSession newSession(AgentSessionContext context) {
        return new Session(context);
    }

    private record Session(AgentSessionContext context) implements AgentSession {
        @Override
        public void onRoundResolved(RoundResult lastRound) {
        }

        @Override
        public Move move(Board board, List<Move> legalMoves) {
            return legalMoves.get(context.rng().nextInt(legalMoves.size()));
        }

        @Override
        public Move guess(Board board, List<Move> opponentLegalMoves) {
            return opponentLegalMoves.get(context.rng().nextInt(opponentLegalMoves.size()));
        }
    }
}
