package com.guesschess.infrastructure.admin.web;

/**
 * white/blackLabel : login pour un compte, "ANONYMOUS"/"COMPUTER_<niveau>" pour les
 * autres cas (le frontend traduit), null tant que la couleur n'a ete revendiquee par
 * personne.
 */
record AdminGameHttpResponse(String id, String variant, String status, String resultWinner, String resultCause,
                              String whiteLabel, String whiteType, String blackLabel, String blackType,
                              String createdAt, String updatedAt) {
}
