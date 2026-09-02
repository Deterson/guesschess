package com.guesschess.infrastructure.account.web;

import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSettingsSnapshot;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.application.account.InvalidLoginException;
import com.guesschess.domain.account.UserId;
import com.guesschess.infrastructure.web.dto.ErrorResponse;
import com.guesschess.infrastructure.websocket.GameMessageMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST du contexte "Compte joueur" (etape 4 de la roadmap), proteges par
 * JWT - separes du flux WebSocket/PlayerToken du contexte "Partie". /games (etape 8)
 * traverse neanmoins vers le contexte "Partie" (GameLifecycleService) : le prefixe
 * /api/account/** et sa protection JWT (voir SecurityConfig) restent du ressort du
 * contexte "Compte joueur", la resolution des parties elle-meme de celui de "Partie".
 */
@RestController
@RequestMapping("/api/account")
class AccountController {

    private final AccountService accountService;
    private final GameLifecycleService gameLifecycleService;
    private final GameMessageMapper mapper;

    AccountController(AccountService accountService, GameLifecycleService gameLifecycleService, GameMessageMapper mapper) {
        this.accountService = accountService;
        this.gameLifecycleService = gameLifecycleService;
        this.mapper = mapper;
    }

    @GetMapping("/me")
    AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
        AccountSnapshot account = accountService.getById(UserId.fromString(jwt.getSubject()));
        return toResponse(account);
    }

    /**
     * Nom d'affichage modifiable (etape 8) - 2 a 32 caracteres, voir
     * AccountService.updateDisplayName.
     */
    @PatchMapping("/me")
    ResponseEntity<?> updateMe(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateDisplayNameHttpRequest request) {
        try {
            AccountSnapshot account = accountService.updateDisplayName(UserId.fromString(jwt.getSubject()), request.displayName());
            return ResponseEntity.ok(toResponse(account));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_DISPLAY_NAME", "Le nom doit contenir entre 2 et 32 caracteres"));
        }
    }

    /**
     * Pose le login d'un compte historique cree avant l'etape 14 (login nullable en
     * base, voir migration V9) - le compte est deja authentifie (JWT de session
     * normal), il lui manque juste ce champ pour sortir du blocage cote frontend.
     * Immuable : un second appel echoue (le compte a deja un login).
     */
    @PatchMapping("/login")
    ResponseEntity<?> setLogin(@AuthenticationPrincipal Jwt jwt, @RequestBody SetLoginHttpRequest request) {
        try {
            AccountSnapshot account = accountService.setLoginForExistingAccount(UserId.fromString(jwt.getSubject()), request.login());
            return ResponseEntity.ok(toResponse(account));
        } catch (InvalidLoginException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.code(), e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("LOGIN_ALREADY_SET", "Ce compte a deja un pseudo"));
        }
    }

    /**
     * Bio libre du profil (etape 14) - 5000 caracteres maximum, voir AccountService.updateBio.
     */
    @PatchMapping("/bio")
    ResponseEntity<?> updateBio(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateBioHttpRequest request) {
        try {
            AccountSnapshot account = accountService.updateBio(UserId.fromString(jwt.getSubject()), request.bio());
            return ResponseEntity.ok(toResponse(account));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_BIO", "La bio ne doit pas depasser 5000 caracteres"));
        }
    }

    private AccountResponse toResponse(AccountSnapshot account) {
        return new AccountResponse(account.id().toString(), account.displayName(), account.login(), account.bio(), account.email());
    }

    /**
     * "Mes parties" (etape 8) - pagine (page/size), triees par recence. size est borne
     * pour eviter qu'un appelant ne demande une page arbitrairement grande.
     */
    @GetMapping("/games")
    List<GameSummaryHttpResponse> games(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        UserId userId = UserId.fromString(jwt.getSubject());
        int boundedSize = Math.clamp(size, 1, 50);
        return gameLifecycleService.listGamesForAccount(userId, Math.max(page, 0), boundedSize).stream()
                .map(summary -> GameSummaryHttpResponseMapper.toResponse(summary, accountService, mapper))
                .toList();
    }

    /**
     * "Parametres" du profil : un seul parametre pour l'instant (rappel clignotant),
     * voir AccountSettingsSnapshot pour l'extension a de futurs parametres.
     */
    @GetMapping("/settings")
    AccountSettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        AccountSettingsSnapshot settings = accountService.getSettings(UserId.fromString(jwt.getSubject()));
        return new AccountSettingsResponse(settings.turnBlinkReminder());
    }

    @PatchMapping("/settings")
    AccountSettingsResponse updateSettings(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateAccountSettingsHttpRequest request) {
        AccountSettingsSnapshot settings = accountService.updateTurnBlinkReminder(UserId.fromString(jwt.getSubject()), request.turnBlinkReminder());
        return new AccountSettingsResponse(settings.turnBlinkReminder());
    }
}
