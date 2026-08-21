package com.guesschess.infrastructure.persistence.jpa;

import java.util.List;

/**
 * Contenu de la colonne JSONB games.state : tout l'etat riche de l'agregat Game qui
 * n'est pas denormalise en colonne scalaire (plateau complet, historiques, round en
 * cours). DTO infra pur, jamais expose au domaine ni au websocket - voir
 * GameJpaMapper pour la conversion depuis/vers Game.Memento.
 */
record GameStateJson(
        BoardJson board,
        List<BoardJson> positionHistory,
        List<MoveJson> moveHistory,
        MoveJson pendingMove,
        boolean guessSubmitted,
        MoveJson pendingGuess,
        RoundResultJson lastRoundResult
) {
}
