package com.guesschess.infrastructure.websocket.dto;

/**
 * from/to null signifie "pas de devinette" (retrait ou timeout cote client).
 */
public record SubmitGuessRequest(String token, String from, String to, String promotion) {
}
