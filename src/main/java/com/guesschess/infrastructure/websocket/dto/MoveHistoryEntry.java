package com.guesschess.infrastructure.websocket.dto;

/**
 * Un coup deja joue dans la partie, en notation algebrique standard (SAN, ex. "Nf3",
 * "exd5", "O-O", "e8=Q", "Qh4#" - voir SanGenerator). color = nom de Color du joueur
 * qui a joue ce coup.
 */
public record MoveHistoryEntry(String color, String san) {
}
