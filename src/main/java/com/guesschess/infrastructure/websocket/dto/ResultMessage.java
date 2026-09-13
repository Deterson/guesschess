package com.guesschess.infrastructure.websocket.dto;

/**
 * fastMate distingue, pour l'affichage cote frontend uniquement, une fin de partie
 * cause="KING_CAPTURED" via fast_mate (un seul coup legal en echec, variante
 * GUESSCHESS) d'une vraie capture de roi jouee - toujours false pour toute autre
 * cause (voir Game.isFastMateResult).
 */
public record ResultMessage(String winner, String cause, boolean fastMate) {
}
