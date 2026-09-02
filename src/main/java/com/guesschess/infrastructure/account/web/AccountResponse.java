package com.guesschess.infrastructure.account.web;

/** login est null uniquement pour un compte historique qui doit encore en choisir un (etape 14). */
record AccountResponse(String id, String displayName, String login, String bio, String email) {
}
