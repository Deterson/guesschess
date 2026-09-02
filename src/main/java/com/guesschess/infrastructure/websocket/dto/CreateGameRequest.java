package com.guesschess.infrastructure.websocket.dto;

/**
 * variant : "GUESSCHESS" ou "NOGUESSMATE" (voir GameVariant) - null traite comme
 * GUESSCHESS par GameController, pour rester tolerant a un client qui n'envoie pas
 * encore ce champ.
 */
public record CreateGameRequest(String variant) {
}
