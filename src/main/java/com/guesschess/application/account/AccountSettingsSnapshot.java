package com.guesschess.application.account;

/**
 * Photo de lecture des parametres d'un compte. Un seul parametre pour l'instant
 * (rappel clignotant "a vous de jouer/deviner") - les prochains s'ajouteront comme
 * autant de champs supplementaires, sans toucher au stockage (voir AccountSettingsRepository).
 */
public record AccountSettingsSnapshot(boolean turnBlinkReminder) {
}
