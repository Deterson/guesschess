package com.guesschess.infrastructure.web.dto;

/**
 * Identite affichable d'un joueur (etape 14, pseudo au-dessus/en-dessous du plateau) -
 * login est toujours non-null pour type=ACCOUNT (fallback sur le displayName pour un
 * compte historique qui n'a pas encore choisi son login, voir GameCreationController)
 * et toujours null pour type=ANONYMOUS (le frontend affiche alors "Anonyme"/
 * "Anonymous" lui-meme, pour rester traduit). null tant que la couleur correspondante
 * n'est pas encore liee a un joueur reel.
 */
public record PlayerInfoHttpResponse(String type, String login) {
}
