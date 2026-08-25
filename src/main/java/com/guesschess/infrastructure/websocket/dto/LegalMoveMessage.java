package com.guesschess.infrastructure.websocket.dto;

/**
 * Un coup legal disponible pour le joueur au trait, en notation algebrique.
 * promotion (nom de PieceType) distingue plusieurs coups legaux partageant le meme
 * from/to (choix de la piece de promotion), null sinon.
 */
public record LegalMoveMessage(String from, String to, String promotion) {
}
