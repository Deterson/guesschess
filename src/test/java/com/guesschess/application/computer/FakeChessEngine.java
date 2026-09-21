package com.guesschess.application.computer;

import com.guesschess.domain.board.Board;
import com.guesschess.domain.move.Move;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Doublure de test pure pour ChessEngine (etape 15) - jamais de vrai process
 * Stockfish. Choisit le premier coup legal par defaut ; alwaysChoose permet de
 * personnaliser la strategie pour un test donne.
 */
public class FakeChessEngine implements ChessEngine {

    /**
     * Cet oracle expose comme agent (etape 20) - pour les tests qui construisent
     * GameLifecycleService/ComputerPlayerService, qui ne connaissent plus que AgentProvider.
     */
    public AgentProvider asAgentProvider() {
        return level -> new EngineBackedAgent(new AgentId("fake", 1), level, rng -> this, this::isAvailable);
    }

    private boolean available = true;
    private BiFunction<Board, List<Move>, Move> strategy = (board, legalMoves) -> legalMoves.get(0);
    private volatile Set<Move> lastMovesToAvoidIfPossible = Set.of();

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void alwaysChoose(BiFunction<Board, List<Move>, Move> strategy) {
        this.strategy = strategy;
    }

    /**
     * Dernier movesToAvoidIfPossible recu par chooseMove (voir ComputerPlayerService,
     * seul appelant en production) - permet aux tests de verifier que le bon ensemble
     * de coups a eviter a ete transmis, sans avoir a deduire ca indirectement du coup
     * choisi (la strategie de test ne le consulte pas forcement).
     */
    public Set<Move> lastMovesToAvoidIfPossible() {
        return lastMovesToAvoidIfPossible;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Move chooseMove(Board board, List<Move> legalMoves, ComputerLevel level, Set<Move> movesToAvoidIfPossible) {
        this.lastMovesToAvoidIfPossible = movesToAvoidIfPossible;
        return strategy.apply(board, legalMoves);
    }
}
