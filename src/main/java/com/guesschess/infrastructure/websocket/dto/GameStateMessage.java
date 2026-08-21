package com.guesschess.infrastructure.websocket.dto;

/**
 * Etat public d'une partie, diffuse sur /topic/games/{gameId}. board[rank][file],
 * rank 0 = rangee 1, file 0 = colonne a ; case vide = null, sinon 2 caracteres
 * couleur+piece (ex. "wP", "bK"). lastRound est null tant qu'aucun round n'a encore
 * ete resolu.
 */
public record GameStateMessage(
        String gameId,
        String[][] board,
        String sideToMove,
        String status,
        ResultMessage result,
        RoundSummaryMessage lastRound
) {
}
