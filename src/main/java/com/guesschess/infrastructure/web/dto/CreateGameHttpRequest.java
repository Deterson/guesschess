package com.guesschess.infrastructure.web.dto;

/**
 * variant : "REGULAR" ou "GUESSMATE" (voir GameVariant), null traite comme
 * REGULAR. color : "WHITE", "BLACK" ou "RANDOM" (resolu cote serveur).
 */
public record CreateGameHttpRequest(String variant, String color) {
}
