package com.guesschess.infrastructure.websocket.dto;

/**
 * Miroir WebSocket de PlayerInfoHttpResponse (etape 14) - login vaut le displayName
 * pour un compte historique qui n'a pas encore choisi le sien (voir
 * PlayersBroadcastService), null pour type=ANONYMOUS.
 */
public record PlayerInfoMessage(String type, String login) {
}
