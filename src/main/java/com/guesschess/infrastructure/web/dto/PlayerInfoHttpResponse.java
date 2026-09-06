package com.guesschess.infrastructure.web.dto;

/**
 * Identite affichable d'un joueur (etape 14, pseudo au-dessus/en-dessous du plateau) -
 * login est toujours non-null pour type=ACCOUNT (fallback sur le displayName pour un
 * compte historique qui n'a pas encore choisi son login, voir GameCreationController)
 * et toujours null pour type=ANONYMOUS/COMPUTER (le frontend affiche alors "Anonyme"/
 * "Anonymous" ou "Ordinateur" lui-meme, pour rester traduit). null tant que la couleur
 * correspondante n'est pas encore liee a un joueur reel. connected reflete la presence
 * WebSocket en direct de cette couleur au moment de la reponse (voir
 * GamePresenceService) - une simple photo, contrairement a /topic/games/{id}/players
 * qui la tient a jour en direct ; toujours true pour type=COMPUTER (etape 15), qui n'a
 * pas de notion de deconnexion. level (etape 15) porte le niveau de l'ordinateur
 * ("EASY"/"MEDIUM"/"HARD"), null pour les deux autres types.
 */
public record PlayerInfoHttpResponse(String type, String login, boolean connected, String level) {
}
