package com.guesschess.infrastructure.web.dto;

/**
 * creatorToken/creatorColor : a utiliser par le createur pour jouer immediatement,
 * deja lie a la creation. Pas de champ "invitation" separe : le lien a partager est la
 * partie elle-meme (/game/{gameId}, sans aucun token), voir GameCreationController.join.
 */
public record CreateGameHttpResponse(String gameId, String variant, String creatorColor, String creatorToken) {
}
