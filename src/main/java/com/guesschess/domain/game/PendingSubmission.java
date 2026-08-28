package com.guesschess.domain.game;

import com.guesschess.domain.move.Move;

/**
 * Ce qu'une couleur donnee a deja soumis pour le round en cours, du seul point de vue
 * de cette couleur (voir Game.mySubmission - jamais expose a l'adversaire ni a un
 * spectateur, sous peine de recreer la fuite anti-triche que GameSnapshot evite
 * deliberement). submitted=false (NONE) : rien soumis pour ce round. submitted=true et
 * move=null : devinette explicitement absente soumise (submitGuess(null)) - distinct
 * de NONE bien que move soit null dans les deux cas.
 */
public record PendingSubmission(boolean submitted, Move move) {

    public static final PendingSubmission NONE = new PendingSubmission(false, null);

    public static PendingSubmission of(Move move) {
        return new PendingSubmission(true, move);
    }
}
