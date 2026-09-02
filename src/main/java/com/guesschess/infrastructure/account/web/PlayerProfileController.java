package com.guesschess.infrastructure.account.web;

import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.UserId;
import com.guesschess.infrastructure.web.dto.ErrorResponse;
import com.guesschess.infrastructure.websocket.GameMessageMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Profil public d'un joueur (etape 15), consultable par login sans authentification -
 * distinct de /api/account/** (le compte courant, authentifie par JWT). Public par
 * defaut (SecurityConfig : seul /api/account/** exige une authentification, tout le
 * reste est permitAll), coherent avec le mode spectateur deja en place sur
 * /api/games/{id}/history et /players.
 */
@RestController
@RequestMapping("/api/players")
class PlayerProfileController {

    private final AccountService accountService;
    private final GameLifecycleService gameLifecycleService;
    private final GameMessageMapper mapper;

    PlayerProfileController(AccountService accountService, GameLifecycleService gameLifecycleService, GameMessageMapper mapper) {
        this.accountService = accountService;
        this.gameLifecycleService = gameLifecycleService;
        this.mapper = mapper;
    }

    @GetMapping("/{login}")
    ResponseEntity<?> profile(@PathVariable String login) {
        Optional<AccountSnapshot> account = accountService.findByLogin(login);
        if (account.isEmpty()) {
            return notFound();
        }
        AccountSnapshot a = account.get();
        return ResponseEntity.ok(new PublicProfileHttpResponse(a.id().toString(), a.displayName(), a.login(), a.bio()));
    }

    /**
     * Parties d'un joueur quelconque (etape 15), meme pagination que
     * GET /api/account/games mais resolue par login plutot que par JWT.
     */
    @GetMapping("/{login}/games")
    ResponseEntity<?> games(@PathVariable String login,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        Optional<AccountSnapshot> account = accountService.findByLogin(login);
        if (account.isEmpty()) {
            return notFound();
        }
        UserId userId = account.get().id();
        int boundedSize = Math.clamp(size, 1, 50);
        List<GameSummaryHttpResponse> games = gameLifecycleService.listGamesForAccount(userId, Math.max(page, 0), boundedSize).stream()
                .map(summary -> GameSummaryHttpResponseMapper.toResponse(summary, accountService, mapper))
                .toList();
        return ResponseEntity.ok(games);
    }

    private ResponseEntity<ErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("PLAYER_NOT_FOUND", "Joueur introuvable"));
    }
}
