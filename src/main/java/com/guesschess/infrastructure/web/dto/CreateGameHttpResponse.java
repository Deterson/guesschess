package com.guesschess.infrastructure.web.dto;

/**
 * creatorToken/creatorColor : a utiliser par le createur pour rejoindre directement sa
 * partie (deja lie). opponentToken/opponentColor : lien d'invitation a usage unique a
 * partager (etape 7 de la roadmap).
 */
public record CreateGameHttpResponse(String gameId, String variant, String creatorColor, String creatorToken,
                                      String opponentColor, String opponentToken) {
}
