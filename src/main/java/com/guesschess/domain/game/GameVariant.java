package com.guesschess.domain.game;

/**
 * GUESSCHESS : regle de base (voir Game) - une devinette correcte annule le coup et
 * passe le trait au devineur, meme si ce coup parait un echec (le roi reste alors en
 * echec, a la merci d'une capture au tour suivant).
 *
 * GUESSMATE : variante ou deviner correctement le coup qui parait un echec met fin a
 * la partie immediatement, victoire du devineur (pas besoin de capturer le roi au
 * tour suivant). Une devinette correcte hors echec se comporte comme en GUESSCHESS.
 */
public enum GameVariant {
    GUESSCHESS,
    GUESSMATE
}
