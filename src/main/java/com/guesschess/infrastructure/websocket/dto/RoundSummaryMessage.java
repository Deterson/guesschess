package com.guesschess.infrastructure.websocket.dto;

/**
 * Round deja resolu : reveler la devinette ici est le mecanisme voulu (cf.
 * GameSnapshot), pas une fuite anti-triche. Pas de champ movePlayed separe : c'est
 * toujours l'exact inverse de guessedCorrectly (voir RoundResult.movePlayed cote
 * domaine), donc redondant a transporter sur le fil.
 */
public record RoundSummaryMessage(
        String mover,
        String guesser,
        String actualFrom,
        String actualTo,
        String guessedFrom,
        String guessedTo,
        boolean guessedCorrectly
) {
}
