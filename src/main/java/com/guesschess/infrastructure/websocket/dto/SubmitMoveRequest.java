package com.guesschess.infrastructure.websocket.dto;

/**
 * promotion est le nom de la PieceType du domaine (ex. "QUEEN"), null pour un coup
 * qui n'est pas une promotion.
 */
public record SubmitMoveRequest(String token, String from, String to, String promotion) {
}
