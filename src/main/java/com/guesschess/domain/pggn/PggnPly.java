package com.guesschess.domain.pggn;

import com.guesschess.domain.piece.Color;

/**
 * Un demi-coup (round) au format PGGN. realSan est null quand le round a ete annule
 * (devinette correcte : aucun coup n'a ete reellement joue) - dans ce cas guessedSan
 * porte le seul texte affiche, entre parentheses. guessedSan est null quand aucune
 * devinette n'a ete soumise pour ce round. Le suffixe +/# est deja inclus dans la
 * chaine concernee (realSan pour un coup reellement joue qui met en echec/mat ;
 * guessedSan uniquement pour le cas terminal Guessmate, ou "#" est ajoute a la main -
 * voir PggnWriter) : ni l'un ni l'autre champ ne porte jamais de suffixe separe.
 */
public record PggnPly(int moveNumber, Color mover, String realSan, String guessedSan) {
}
