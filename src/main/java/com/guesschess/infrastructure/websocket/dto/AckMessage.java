package com.guesschess.infrastructure.websocket.dto;

/**
 * Accuse prive envoye a l'expediteur quand sa soumission (coup ou devinette) est
 * enregistree mais que le round attend encore l'autre moitie de la paire.
 */
public record AckMessage(String status) {
}
