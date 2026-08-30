package com.guesschess.infrastructure.web.dto;

/**
 * variant : "GUESSCHESS" ou "GUESSMATE" (voir GameVariant), null traite comme
 * GUESSCHESS. color : "WHITE", "BLACK" ou "RANDOM" (resolu cote serveur).
 */
public record CreateGameHttpRequest(String variant, String color) {
}
