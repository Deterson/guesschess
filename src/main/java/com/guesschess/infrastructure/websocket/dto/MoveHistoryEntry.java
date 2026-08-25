package com.guesschess.infrastructure.websocket.dto;

/**
 * Un coup deja joue dans la partie, en notation algebrique. piece/captured sont le
 * code 2 caracteres couleur+piece (ex. "wP", "bK"), captured null si le coup n'etait
 * pas une capture. type = nom de MoveType (utile pour distinguer roque/en passant/
 * promotion cote client). promotion (nom de PieceType) null hors promotion.
 */
public record MoveHistoryEntry(String from, String to, String piece, String captured, String type, String promotion) {
}
