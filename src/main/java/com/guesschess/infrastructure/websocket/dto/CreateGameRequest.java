package com.guesschess.infrastructure.websocket.dto;

/**
 * variant : "REGULAR" ou "GUESSMATE" (voir GameVariant) - null traite comme
 * REGULAR par GameController, pour rester tolerant a un client qui n'envoie pas
 * encore ce champ.
 */
public record CreateGameRequest(String variant) {
}
