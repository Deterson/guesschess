package com.guesschess.infrastructure.account.web;

import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
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
        return new AccountResponse(account.id().toString(), account.displayName(), account.email());
    }

    /**
     * Nom d'affichage modifiable (etape 8) - 3 caracteres minimum, voir
     * AccountService.updateDisplayName.
     */
    @PatchMapping("/me")
    ResponseEntity<?> updateMe(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateDisplayNameHttpRequest request) {
        try {
            AccountSnapshot account = accountService.updateDisplayName(UserId.fromString(jwt.getSubject()), request.displayName());
            return ResponseEntity.ok(new AccountResponse(account.id().toString(), account.displayName(), account.email()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_DISPLAY_NAME", "Le nom doit contenir au moins 3 caracteres"));
        }
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
                .map(this::toResponse)
                .toList();
    }

    private GameSummaryHttpResponse toResponse(GameLifecycleService.GameSummary summary) {
        String opponentName = switch (summary.opponent()) {
            case null -> null;
            case PlayerRef.Account account -> accountService.getById(account.userId()).displayName();
            case PlayerRef.Anonymous anonymous -> null;
        };
        GameSummaryHttpResponse.OpponentType opponentType = switch (summary.opponent()) {
            case null -> GameSummaryHttpResponse.OpponentType.NONE;
            case PlayerRef.Account account -> GameSummaryHttpResponse.OpponentType.ACCOUNT;
            case PlayerRef.Anonymous anonymous -> GameSummaryHttpResponse.OpponentType.ANONYMOUS;
        };
        return new GameSummaryHttpResponse(
                summary.gameId().toString(), summary.myColor().name(), opponentName, opponentType,
                summary.outcome().name(), mapper.toBoardCells(summary.board()));
    }
}
