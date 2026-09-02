package com.guesschess.application.account;

import com.guesschess.domain.account.UserId;

/**
 * Photo de lecture d'un compte, pour ne pas exposer directement l'aggregate User a
 * l'infrastructure web. login est null uniquement pour un compte historique qui n'a
 * pas encore choisi le sien (etape 14) - un tel compte doit rester bloque cote
 * frontend tant que ce n'est pas fait (voir CLAUDE.md).
 */
public record AccountSnapshot(UserId id, String displayName, String login, String bio, String email) {
}
