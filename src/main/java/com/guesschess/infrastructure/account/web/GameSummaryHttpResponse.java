package com.guesschess.infrastructure.account.web;

/**
 * Une ligne de "Mes parties" (etape 8 de la roadmap) - opponentName est null tant que
 * personne n'a encore rejoint l'autre couleur (partie en attente d'un adversaire).
 */
record GameSummaryHttpResponse(String gameId, String myColor, String opponentName, String outcome, String[][] board) {
}
