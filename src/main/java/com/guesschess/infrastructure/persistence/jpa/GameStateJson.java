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
        List<PositionRecordJson> positionHistory,
        MoveJson pendingMove,
        boolean guessSubmitted,
        MoveJson pendingGuess,
        List<RoundResultJson> roundHistory,
        MoveJson whiteGuessedMove,
        int whiteGuessedMoveStreak,
        MoveJson blackGuessedMove,
        int blackGuessedMoveStreak,
        String drawOfferedBy,
        String rematchOfferedBy,
        String rematchGameId,
        Long timeControlBaseMillis,
        Long timeControlIncrementMillis,
        // Long (pas long) : absents du JSON des parties persistees avant l'etape 12,
        // comme timeControlBaseMillis ci-dessus - un primitif ferait echouer la
        // deserialisation (MismatchedInputException) au lieu de degrader en 0/aucune
        // pendule, voir GameJpaMapper.toDomain.
        Long whiteMillisRemaining,
        Long blackMillisRemaining,
        String clockRunningFor,
        Long clockRunningSinceEpochMillis
) {
}
