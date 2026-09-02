package com.guesschess.domain.account;

/**
 * Cle connue d'un parametre de compte (etape "Parametres" du profil). La valeur reste
 * stockee comme une simple chaine (voir AccountSettingsRepository) - ce qui varie d'un
 * parametre a l'autre, c'est la forme de widget cote frontend (coche, echelle, texte,
 * radio...), pas le stockage.
 */
public enum AccountSettingKey {
    TURN_BLINK_REMINDER
}
