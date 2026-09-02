package com.guesschess.infrastructure.websocket.dto;

/**
 * Diffuse sur /topic/games/{gameId}/players (etape 14) a chaque changement d'identite
 * d'un joueur - un adversaire qui rejoint (GameCreationController.join) ou un joueur
 * anonyme qui se connecte a un compte en pleine partie (voir PlayersBroadcastService).
 * Separe de GameStateMessage (diffuse bien plus souvent, a chaque round) pour ne pas
 * l'alourdir d'une information qui ne change quasiment jamais une fois la partie
 * commencee.
 */
public record GamePlayersMessage(PlayerInfoMessage white, PlayerInfoMessage black) {
}
