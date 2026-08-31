package com.guesschess.infrastructure.web.dto;

/**
 * Un round de l'historique detaille d'une partie (etape 11 de la roadmap).
 * realSan est null quand le round a ete annule (devinette correcte) - aucun coup
 * n'a alors ete reellement joue. guessedSan/guessedFrom/guessedTo sont null quand
 * aucune devinette n'a ete soumise pour ce round. Pas de champ movePlayed separe :
 * c'est toujours l'exact inverse de guessedCorrectly (voir RoundSummaryMessage),
 * redondant a transporter. boardAfter est null uniquement pour le round terminal
 * Guessmate (roi capture via devinette correcte en echec) : la partie se termine
 * immediatement, aucun nouvel instantane de position n'est pris.
 */
public record GameHistoryEntryHttpResponse(
        int moveNumber,
        String mover,
        String guesser,
        String actualFrom,
        String actualTo,
        String realSan,
        String guessedFrom,
        String guessedTo,
        String guessedSan,
        boolean guessedCorrectly,
        String[][] boardAfter
) {
}
