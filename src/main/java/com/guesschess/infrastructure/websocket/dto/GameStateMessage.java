package com.guesschess.infrastructure.websocket.dto;

import java.util.List;

/**
 * Etat public d'une partie, diffuse sur /topic/games/{gameId}. board[rank][file],
 * rank 0 = rangee 1, file 0 = colonne a ; case vide = null, sinon 2 caracteres
 * couleur+piece (ex. "wP", "bK"). lastRound est null tant qu'aucun round n'a encore
 * ete resolu. legalMoves liste les coups legaux du joueur au trait (sideToMove) :
 * a la fois pour son propre coup et pour la devinette de son adversaire, qui porte
 * sur les memes coups.
 */
public record GameStateMessage(
        String gameId,
        String variant,
        String[][] board,
        String sideToMove,
        String status,
        ResultMessage result,
        RoundSummaryMessage lastRound,
        List<LegalMoveMessage> legalMoves,
        List<MoveHistoryEntry> moveHistory
) {
}
