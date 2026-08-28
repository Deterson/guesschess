package com.guesschess.infrastructure.websocket.dto;

/**
 * token identifie le demandeur pour joindre a l'etat public sa propre soumission en
 * attente (voir MySubmissionMessage) - null pour un spectateur, ou un client qui n'a
 * pas encore ce champ (compatibilite : payload entier absent aussi tolere, voir
 * GameController.viewGame).
 */
public record ViewGameRequest(String token) {
}
