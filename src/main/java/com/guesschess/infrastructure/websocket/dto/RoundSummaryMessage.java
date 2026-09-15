package com.guesschess.infrastructure.websocket.dto;

/**
 * Round deja resolu : reveler la devinette ici est le mecanisme voulu (cf.
 * GameSnapshot), pas une fuite anti-triche. Pas de champ movePlayed separe : c'est
 * toujours l'exact inverse de guessedCorrectly (voir RoundResult.movePlayed cote
 * domaine), donc redondant a transporter sur le fil. guessedSan est la notation SAN
 * du coup devine (calculee sur le plateau juste avant ce round, voir
 * GameMessageMapper) - guessedFrom/guessedTo restent utiles cote frontend pour
 * surligner les cases concernees sur l'echiquier.
 */
public record RoundSummaryMessage(
        String mover,
        String guesser,
        String actualFrom,
        String actualTo,
        String guessedFrom,
        String guessedTo,
        String guessedSan,
        boolean guessedCorrectly
) {
}
