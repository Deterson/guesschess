package com.guesschess.infrastructure.websocket.dto;

/**
 * Round deja resolu : reveler la devinette ici est le mecanisme voulu (cf.
 * GameSnapshot), pas une fuite anti-triche.
 */
public record RoundSummaryMessage(
        String mover,
        String guesser,
        String actualFrom,
        String actualTo,
        String guessedFrom,
        String guessedTo,
        boolean guessedCorrectly,
        boolean movePlayed
) {
}
