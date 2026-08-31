package com.guesschess.infrastructure.account.web;

/**
 * Une ligne de "Mes parties" (etape 8 de la roadmap) - opponentName/opponentType sont
 * null/NONE tant que personne n'a encore rejoint l'autre couleur (partie en attente
 * d'un adversaire). opponentType distingue ACCOUNT (opponentName porte alors le
 * displayName reel) de ANONYMOUS (opponentName reste null : jamais de libelle en dur
 * cote serveur, le frontend traduit lui-meme le placeholder - voir CLAUDE.md) - un
 * champ structurel derive de PlayerRef plutot qu'un texte, pour ne jamais dependre du
 * displayName librement choisi par un compte (non falsifiable en se renommant
 * "Anonymous").
 */
record GameSummaryHttpResponse(String gameId, String myColor, String opponentName, OpponentType opponentType, String outcome, String[][] board) {

    enum OpponentType {
        NONE, ACCOUNT, ANONYMOUS
    }
}
