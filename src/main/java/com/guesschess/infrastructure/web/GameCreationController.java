package com.guesschess.infrastructure.web;

import com.guesschess.application.CreatedGame;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.JoinResult;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.UnknownPlayerTokenException;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.security.HttpPlayerIdentityResolver;
import com.guesschess.infrastructure.web.dto.CreateGameHttpRequest;
import com.guesschess.infrastructure.web.dto.CreateGameHttpResponse;
import com.guesschess.infrastructure.web.dto.ErrorResponse;
import com.guesschess.infrastructure.web.dto.JoinGameHttpRequest;
import com.guesschess.infrastructure.web.dto.JoinGameHttpResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Creation de partie et acceptation d'invitation (etape 7 de la roadmap), en REST plutot
 * que STOMP (contrairement a GameController) car ce flux doit s'enchainer avec une
 * redirection OAuth complete du navigateur - pas naturel a faire tenir dans une session
 * STOMP. L'endpoint STOMP /app/games.create existant n'est pas touche : il reste
 * utilise tel quel par les tests d'integration, ce nouveau flux ne le remplace que cote
 * frontend.
 */
@RestController
@RequestMapping("/api/games")
class GameCreationController {

    private final GameLifecycleService gameLifecycleService;
    private final HttpPlayerIdentityResolver identityResolver;

    GameCreationController(GameLifecycleService gameLifecycleService, HttpPlayerIdentityResolver identityResolver) {
        this.gameLifecycleService = gameLifecycleService;
        this.identityResolver = identityResolver;
    }

    @PostMapping
    ResponseEntity<CreateGameHttpResponse> createGame(@RequestBody(required = false) CreateGameHttpRequest request,
                                                        HttpServletRequest httpRequest,
                                                        @AuthenticationPrincipal Jwt jwt) {
        GameVariant variant = request == null || request.variant() == null
                ? GameVariant.GUESSCHESS
                : GameVariant.valueOf(request.variant());
        Color creatorColor = resolveColor(request == null ? null : request.color());
        PlayerRef creator = identityResolver.resolve(httpRequest, jwt);

        CreatedGame created = gameLifecycleService.createGame(variant, creatorColor, creator);
        Color opponentColor = creatorColor.opposite();
        String creatorToken = (creatorColor == Color.WHITE ? created.whiteToken() : created.blackToken()).toString();
        String opponentToken = (opponentColor == Color.WHITE ? created.whiteToken() : created.blackToken()).toString();

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateGameHttpResponse(
                created.gameId().toString(), created.variant().name(),
                creatorColor.name(), creatorToken,
                opponentColor.name(), opponentToken));
    }

    @PostMapping("/{gameId}/join")
    ResponseEntity<?> join(@PathVariable String gameId, @RequestBody JoinGameHttpRequest request,
                            HttpServletRequest httpRequest, @AuthenticationPrincipal Jwt jwt) {
        PlayerRef requester = identityResolver.resolve(httpRequest, jwt);
        JoinResult result;
        try {
            result = gameLifecycleService.joinGame(PlayerToken.fromString(request.token()), requester);
        } catch (UnknownPlayerTokenException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("UNKNOWN_TOKEN", "Invitation introuvable"));
        }
        if (!result.gameId().toString().equals(gameId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("UNKNOWN_TOKEN", "Invitation introuvable"));
        }
        if (!result.linkedToRequester()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("ALREADY_LINKED", "Cette invitation a deja ete utilisee"));
        }
        return ResponseEntity.ok(new JoinGameHttpResponse(result.gameId().toString(), result.color().name(), request.token()));
    }

    private Color resolveColor(String requested) {
        if (requested == null || "RANDOM".equals(requested)) {
            return ThreadLocalRandom.current().nextBoolean() ? Color.WHITE : Color.BLACK;
        }
        return Color.valueOf(requested);
    }
}
