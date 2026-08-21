package com.guesschess.application.account;

import com.guesschess.domain.account.UserId;

/**
 * Photo de lecture d'un compte, pour ne pas exposer directement l'aggregate User a
 * l'infrastructure web.
 */
public record AccountSnapshot(UserId id, String displayName, String email) {
}
