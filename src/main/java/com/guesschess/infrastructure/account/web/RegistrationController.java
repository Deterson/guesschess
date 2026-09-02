package com.guesschess.infrastructure.account.web;

import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.application.account.InvalidLoginException;
import com.guesschess.domain.game.GameId;
import com.guesschess.infrastructure.security.JwtService;
import com.guesschess.infrastructure.web.dto.ErrorResponse;
import com.guesschess.infrastructure.websocket.PlayersBroadcastService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Finalisation de l'inscription (etape 14) - endpoint volontairement hors de
 * /api/account/** (donc non soumis a `.authenticated()`, voir SecurityConfig) : au
 * moment de l'appel, aucun compte n'existe encore pour cette identite OAuth, un JWT
 * de session serait donc impossible a presenter. L'identite verifiee est portee par
 * le pendingToken lui-meme (voir JwtService.decodePendingRegistrationToken), emis par
 * OAuthLoginSuccessHandler uniquement apres un login OAuth2 reussi.
 */
@RestController
@RequestMapping("/api/registration")
class RegistrationController {

    private final AccountService accountService;
    private final JwtService jwtService;
    private final GameAccessRepository gameAccessRepository;
    private final PlayersBroadcastService playersBroadcastService;

    RegistrationController(AccountService accountService, JwtService jwtService, GameAccessRepository gameAccessRepository,
                            PlayersBroadcastService playersBroadcastService) {
        this.accountService = accountService;
        this.jwtService = jwtService;
        this.gameAccessRepository = gameAccessRepository;
        this.playersBroadcastService = playersBroadcastService;
    }

    @PostMapping("/complete")
    ResponseEntity<?> complete(@RequestBody CompleteRegistrationHttpRequest request) {
        JwtService.PendingRegistration pending;
        try {
            pending = jwtService.decodePendingRegistrationToken(request.pendingToken());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("PENDING_TOKEN_INVALID", "Session d'inscription expiree, reconnecte-toi"));
        }

        AccountSnapshot account;
        try {
            account = accountService.completeRegistration(pending.provider(), pending.externalId(), pending.email(), request.login());
        } catch (InvalidLoginException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.code(), e.getMessage()));
        }

        // Fusion identite anonyme -> compte (etape 8), differee ici depuis
        // OAuthLoginSuccessHandler puisqu'aucun compte n'existait encore a ce moment
        // (voir la doc de la classe) - meme logique, juste posee au moment ou le
        // compte est effectivement cree.
        if (pending.anonymousId() != null) {
            List<GameId> relinkedGames = gameAccessRepository.relinkAnonymousToAccount(
                    new PlayerRef.Anonymous(pending.anonymousId()), new PlayerRef.Account(account.id()));
            // Meme raison que OAuthLoginSuccessHandler : l'inscription peut se
            // terminer en pleine partie jouee anonymement (etape 14).
            relinkedGames.forEach(playersBroadcastService::broadcastPlayers);
        }

        String token = jwtService.generateToken(account.id(), account.displayName());
        AccountResponse response = new AccountResponse(account.id().toString(), account.displayName(), account.login(), account.bio(), account.email());
        return ResponseEntity.ok(new CompleteRegistrationHttpResponse(token, response));
    }
}
