package com.guesschess.infrastructure.websocket.dto;

import java.util.List;

/**
 * Etat public d'une partie, diffuse sur /topic/games/{gameId}. board[rank][file],
 * rank 0 = rangee 1, file 0 = colonne a ; case vide = null, sinon 2 caracteres
 * couleur+piece (ex. "wP", "bK"). lastRound est null tant qu'aucun round n'a encore
 * ete resolu. legalMoves liste les coups legaux du joueur au trait (sideToMove) :
 * a la fois pour son propre coup et pour la devinette de son adversaire, qui porte
 * sur les memes coups. full indique si les deux couleurs sont deja liees a un
 * joueur reel - permet au frontend de masquer le bouton "Rejoindre cette partie"
 * cote spectateur sans dependre d'un appel REST separe. mySubmission vaut toujours
 * MySubmissionMessage.NONE dans ce message quand il est diffuse publiquement sur ce
 * topic - seule la reponse privee a /app/games/{id}/view le renseigne (voir
 * MySubmissionMessage). roundCount est le nombre de rounds deja resolus (y compris
 * les rounds annules) - permet au frontend de detecter qu'un nouveau round vient
 * d'etre resolu et de refetch l'historique detaille (GET /api/games/{id}/history)
 * sans avoir a diffuser cet historique complet sur chaque message d'etat. inCheck
 * indique si sideToMove est actuellement en echec, pour surligner son roi cote
 * frontend (toujours false une fois la partie terminee). drawOfferedBy ("WHITE"/
 * "BLACK"/null) est la couleur ayant une offre de nulle en attente, publique (pas
 * une fuite anti-triche comme mySubmission : les deux joueurs doivent la voir).
 * rematchOfferedBy ("WHITE"/"BLACK"/null) est la couleur ayant propose une revanche
 * (uniquement pertinent une fois status=FINISHED) ; rematchGameId est l'identifiant
 * de la nouvelle partie une fois les deux couleurs d'accord - le frontend y navigue
 * automatiquement des qu'il le voit apparaitre (voir Game.confirmRematch). timeControl
 * (etape 12) est null pour une partie par correspondance (pas de pendule) ;
 * whiteMillisRemaining/blackMillisRemaining sont les temps restants tels que connus du
 * serveur au moment de ce message (pas recalcules en continu), clockRunningFor
 * ("WHITE"/"BLACK"/null) la couleur dont la pendule tourne actuellement (null si aucune
 * - correspondance, partie pas encore complete, ou terminee), et serverTimeMs
 * l'horodatage serveur de ce message : le frontend s'en sert pour corriger le decalage
 * avec son horloge locale et faire defiler l'affichage lui-meme, sans jamais faire
 * autorite (voir GameSnapshot).
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
        List<MoveHistoryEntry> moveHistory,
        boolean full,
        MySubmissionMessage mySubmission,
        int roundCount,
        boolean inCheck,
        String drawOfferedBy,
        String rematchOfferedBy,
        String rematchGameId,
        TimeControlMessage timeControl,
        long whiteMillisRemaining,
        long blackMillisRemaining,
        String clockRunningFor,
        long serverTimeMs
) {
}
