package com.guesschess.infrastructure.account.web;

/** Profil public par login (etape 15) - jamais l'email, contrairement a AccountResponse. */
record PublicProfileHttpResponse(String id, String displayName, String login, String bio) {
}
