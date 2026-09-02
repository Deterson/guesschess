package com.guesschess.application.account;

/**
 * Login refuse (etape 14) - le code distingue le format invalide, le mot reserve
 * ("Anonymous"/"Anonyme") et l'unicite, pour que l'infrastructure web renvoie un
 * message adapte a chaque cas plutot qu'un message generique.
 */
public class InvalidLoginException extends RuntimeException {

    private final String code;

    public InvalidLoginException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
