package com.guesschess.infrastructure.security;

import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.infrastructure.security.oauth.OAuthAttributes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * A la reussite d'un login OAuth2 (Google/GitHub) : find-or-create le compte, emet un
 * JWT et redirige vers le frontend avec le token en fragment d'URL (jamais en query
 * string, pour eviter qu'il finisse dans des logs serveur/proxy). Le frontend
 * (etape 5) lira ce fragment cote client.
 */
@Component
class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountService accountService;
    private final JwtService jwtService;
    private final GameAccessRepository gameAccessRepository;
    private final String postLoginRedirectUri;

    OAuthLoginSuccessHandler(AccountService accountService, JwtService jwtService, GameAccessRepository gameAccessRepository,
                              @Value("${app.oauth.post-login-redirect-uri}") String postLoginRedirectUri) {
        this.accountService = accountService;
        this.jwtService = jwtService;
        this.gameAccessRepository = gameAccessRepository;
        this.postLoginRedirectUri = postLoginRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuthAttributes attributes = OAuthAttributes.from(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getPrincipal().getAttributes());

        AccountSnapshot account = accountService.findOrCreateByOAuthIdentity(
                attributes.provider(), attributes.externalId(), attributes.displayName(), attributes.email());

        // Fusion identite anonyme -> compte (etape 8) : toute partie deja liee au
        // cookie anonyme de CE navigateur bascule vers le compte qui vient de se
        // connecter, pour apparaitre dans "Mes parties". Seule exception documentee a
        // l'immuabilite du lien - voir GameAccessRepository.relinkAnonymousToAccount.
        AnonymousId anonymousId = (AnonymousId) request.getAttribute(AnonymousIdentityFilter.REQUEST_ATTRIBUTE);
        gameAccessRepository.relinkAnonymousToAccount(new PlayerRef.Anonymous(anonymousId), new PlayerRef.Account(account.id()));

        String token = jwtService.generateToken(account.id(), account.displayName());
        String redirectUrl = postLoginRedirectUri + "#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}
