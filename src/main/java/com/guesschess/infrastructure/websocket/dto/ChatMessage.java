package com.guesschess.infrastructure.websocket.dto;

/**
 * Diffuse tel quel sur /topic/games/{gameId}/chat, jamais persiste ni journalise
 * cote serveur - color identifie l'auteur (WHITE/BLACK), jamais un pseudo ou une
 * identite de compte.
 */
public record ChatMessage(String color, String text) {
}
