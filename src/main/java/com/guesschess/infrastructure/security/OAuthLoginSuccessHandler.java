package com.guesschess.infrastructure.security;

import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.game.GameId;
import com.guesschess.infrastructure.security.oauth.OAuthAttributes;
import com.guesschess.infrastructure.websocket.PlayersBroadcastService;
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
import java.util.List;
import java.util.Optional;

/**
 * A la reussite d'un login OAuth2 (Google/GitHub) : redirige vers le frontend avec,
 * en fragment d'URL (jamais en query string, pour eviter qu'il finisse dans des logs
 * serveur/proxy), soit un jeton de session (compte deja existant), soit un jeton
 * d'inscription en attente (etape 14 : aucun compte ne correspond encore a cette
 * identite OAuth) - dans ce second cas, rien n'est cree en base ici ; le frontend
 * redirige vers l'ecran "choisis ton pseudo", et seule sa soumission valide (voir
 * RegistrationController) cree effectivement le compte.
 */
@Component
class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountService accountService;
    private final JwtService jwtService;
    private final GameAccessRepository gameAccessRepository;
    private final PlayersBroadcastService playersBroadcastService;
    private final String postLoginRedirectUri;

    OAuthLoginSuccessHandler(AccountService accountService, JwtService jwtService, GameAccessRepository gameAccessRepository,
                              PlayersBroadcastService playersBroadcastService,
                              @Value("${app.oauth.post-login-redirect-uri}") String postLoginRedirectUri) {
        this.accountService = accountService;
        this.jwtService = jwtService;
        this.gameAccessRepository = gameAccessRepository;
        this.playersBroadcastService = playersBroadcastService;
        this.postLoginRedirectUri = postLoginRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuthAttributes attributes = OAuthAttributes.from(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getPrincipal().getAttributes());

        AnonymousId anonymousId = (AnonymousId) request.getAttribute(AnonymousIdentityFilter.REQUEST_ATTRIBUTE);
        Optional<AccountSnapshot> existing = accountService.findByOAuthIdentity(attributes.provider(), attributes.externalId());

        String redirectUrl;
        if (existing.isPresent()) {
            AccountSnapshot account = existing.get();
            // Fusion identite anonyme -> compte (etape 8) : toute partie deja liee au
            // cookie anonyme de CE navigateur bascule vers le compte qui vient de se
            // connecter, pour apparaitre dans "Mes parties". Seule exception documentee a
            // l'immuabilite du lien - voir GameAccessRepository.relinkAnonymousToAccount.
            List<GameId> relinkedGames = gameAccessRepository.relinkAnonymousToAccount(
                    new PlayerRef.Anonymous(anonymousId), new PlayerRef.Account(account.id()));
            // Un joueur anonyme peut se connecter EN PLEINE PARTIE (etape 14) : sans
            // cette diffusion, l'adversaire et les spectateurs deja connectes ne
            // verraient le nouveau pseudo qu'au prochain rechargement de page.
            relinkedGames.forEach(playersBroadcastService::broadcastPlayers);
            String token = jwtService.generateToken(account.id(), account.displayName());
            redirectUrl = postLoginRedirectUri + "#token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        } else {
            String pendingToken = jwtService.generatePendingRegistrationToken(
                    attributes.provider(), attributes.externalId(), attributes.email(), anonymousId);
            redirectUrl = postLoginRedirectUri + "#pendingToken=" + URLEncoder.encode(pendingToken, StandardCharsets.UTF_8);
        }
        response.sendRedirect(redirectUrl);
    }
}
