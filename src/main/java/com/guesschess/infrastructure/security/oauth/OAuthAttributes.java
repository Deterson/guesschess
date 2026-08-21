package com.guesschess.infrastructure.security.oauth;

import com.guesschess.domain.account.OAuthProvider;

import java.util.Map;

/**
 * Extraction des attributs bruts OAuth2 selon le fournisseur (Google : sub/name/email
 * ; GitHub : id/login/email, email nullable - GitHub ne le garantit pas dans le
 * profil standard). L'identifiant unique reste toujours (provider, externalId),
 * jamais l'email.
 */
public record OAuthAttributes(OAuthProvider provider, String externalId, String displayName, String email) {

    public static OAuthAttributes from(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "google" -> new OAuthAttributes(
                    OAuthProvider.GOOGLE,
                    (String) attributes.get("sub"),
                    (String) attributes.get("name"),
                    (String) attributes.get("email"));
            case "github" -> new OAuthAttributes(
                    OAuthProvider.GITHUB,
                    String.valueOf(attributes.get("id")),
                    (String) attributes.get("login"),
                    (String) attributes.get("email"));
            default -> throw new IllegalArgumentException("unsupported OAuth2 registration: " + registrationId);
        };
    }
}
