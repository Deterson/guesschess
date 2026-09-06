package com.guesschess.infrastructure.websocket.dto;

/**
 * Miroir WebSocket de PlayerInfoHttpResponse (etape 14, level ajoute etape 15) - login
 * vaut le displayName pour un compte historique qui n'a pas encore choisi le sien (voir
 * PlayersBroadcastService), null pour type=ANONYMOUS/COMPUTER. connected reflete la
 * presence WebSocket en direct de cette couleur (voir GamePresenceService), toujours
 * true pour type=COMPUTER. level ("EASY"/"MEDIUM"/"HARD") non-null uniquement pour
 * type=COMPUTER.
 */
public record PlayerInfoMessage(String type, String login, boolean connected, String level) {
}
