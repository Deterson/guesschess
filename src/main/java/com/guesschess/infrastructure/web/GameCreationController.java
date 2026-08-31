package com.guesschess.infrastructure.web;

import com.guesschess.application.CreatedGame;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.JoinResult;
import com.guesschess.application.MyAccess;
import com.guesschess.application.NoOpenColorException;
import com.guesschess.application.PlayerRef;
import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.game.RoundResult;
import com.guesschess.domain.pggn.PggnPly;
import com.guesschess.domain.pggn.PggnWriter;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.security.HttpPlayerIdentityResolver;
import com.guesschess.infrastructure.web.dto.CreateGameHttpRequest;
import com.guesschess.infrastructure.web.dto.CreateGameHttpResponse;
import com.guesschess.infrastructure.web.dto.ErrorResponse;
import com.guesschess.infrastructure.web.dto.GameHistoryEntryHttpResponse;
import com.guesschess.infrastructure.web.dto.GameHistoryHttpResponse;
import com.guesschess.infrastructure.web.dto.JoinGameHttpResponse;
import com.guesschess.infrastructure.web.dto.MyAccessHttpResponse;
import com.guesschess.infrastructure.websocket.GameMessageMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creation de partie et acceptation d'invitation (etape 7 de la roadmap), en REST plutot
 * que STOMP (contrairement a GameController) car ce flux doit s'enchainer avec une
 * redirection OAuth complete du navigateur - pas naturel a faire tenir dans une session
 * STOMP. L'endpoint STOMP /app/games.create existant n'est pas touche : il reste
 * utilise tel quel par les tests d'integration, ce nouveau flux ne le remplace que cote
 * frontend.
 *
 * Aucun endpoint ici n'expose de PlayerToken dans une URL ou un parametre de requete :
 * le token ne quitte jamais un corps de reponse JSON (create/join), jamais un lien
 * partageable. join n'a meme pas besoin qu'on le lui fournisse - voir
 * GameLifecycleService.joinGame(GameId, PlayerRef).
 */
@RestController
@RequestMapping("/api/games")
class GameCreationController {

    private final GameLifecycleService gameLifecycleService;
    private final HttpPlayerIdentityResolver identityResolver;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameMessageMapper mapper;

    GameCreationController(GameLifecycleService gameLifecycleService, HttpPlayerIdentityResolver identityResolver,
                            SimpMessagingTemplate messagingTemplate, GameMessageMapper mapper) {
        this.gameLifecycleService = gameLifecycleService;
        this.identityResolver = identityResolver;
        this.messagingTemplate = messagingTemplate;
        this.mapper = mapper;
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
        String creatorToken = (creatorColor == Color.WHITE ? created.whiteToken() : created.blackToken()).toString();

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateGameHttpResponse(
                created.gameId().toString(), created.variant().name(), creatorColor.name(), creatorToken));
    }

    @PostMapping("/{gameId}/join")
    ResponseEntity<?> join(@PathVariable String gameId, HttpServletRequest httpRequest, @AuthenticationPrincipal Jwt jwt) {
        PlayerRef requester = identityResolver.resolve(httpRequest, jwt);
        JoinResult result;
        GameId id = GameId.fromString(gameId);
        try {
            result = gameLifecycleService.joinGame(id, requester);
        } catch (GameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("GAME_NOT_FOUND", "Partie introuvable"));
        } catch (NoOpenColorException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("GAME_FULL", "Cette partie est deja complete"));
        }
        if (!result.linkedToRequester()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("GAME_FULL", "Cette partie est deja complete"));
        }
        broadcastGameState(id);
        return ResponseEntity.ok(new JoinGameHttpResponse(result.gameId().toString(), result.color().name(), result.token().toString()));
    }

    /**
     * Diffuse l'etat courant sur /topic/games/{gameId} apres qu'une couleur vient
     * d'etre liee (join REST, hors du flux STOMP submitMove/submitGuess qui diffuse
     * deja apres resolution d'un round) - permet aux spectateurs deja connectes de
     * voir en direct que la partie est complete (GameStateMessage.full) sans devoir
     * recharger la page.
     */
    private void broadcastGameState(GameId gameId) {
        GameSnapshot snapshot = gameLifecycleService.viewGame(gameId);
        boolean full = gameLifecycleService.isFull(gameId);
        messagingTemplate.convertAndSend("/topic/games/" + gameId, mapper.toGameStateMessage(snapshot, full));
    }

    /**
     * Retrouve le jeton/couleur du visiteur courant pour gameId a partir de sa seule
     * identite (etape 7) - permet a un joueur anonyme qui a perdu l'URL de sa partie
     * (onglet ferme) de la retrouver via /game/{gameId} seul, sans jeton dans l'URL.
     */
    @GetMapping("/{gameId}/my-access")
    ResponseEntity<?> myAccess(@PathVariable String gameId, HttpServletRequest httpRequest, @AuthenticationPrincipal Jwt jwt) {
        PlayerRef requester = identityResolver.resolve(httpRequest, jwt);
        Optional<MyAccess> access;
        try {
            access = gameLifecycleService.findMyAccess(GameId.fromString(gameId), requester);
        } catch (GameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("GAME_NOT_FOUND", "Partie introuvable"));
        }
        if (access.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NO_ACCESS", "Vous n'etes lie a aucune couleur de cette partie"));
        }
        MyAccess found = access.get();
        return ResponseEntity.ok(new MyAccessHttpResponse(found.color().name(), found.token().toString()));
    }

    /**
     * Export PGGN (etape 10 de la roadmap) - texte brut, lecture seule et accessible
     * sans jeton comme my-access/viewGame (le mode spectateur n'a jamais requis
     * d'authentification).
     */
    @GetMapping(value = "/{gameId}/pggn", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<?> pggn(@PathVariable String gameId) {
        try {
            String pggn = gameLifecycleService.exportPggn(GameId.fromString(gameId));
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(pggn);
        } catch (GameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("GAME_NOT_FOUND", "Partie introuvable"));
        }
    }

    /**
     * Historique detaille round par round (etape 11 de la roadmap) - lecture seule et
     * accessible sans jeton comme pggn/my-access. Alimente le panneau de navigation
     * cote frontend (liste de coups cliquable façon PGGN + fantome de la devinette).
     */
    @GetMapping("/{gameId}/history")
    ResponseEntity<?> history(@PathVariable String gameId) {
        try {
            GameLifecycleService.GameHistorySnapshot snapshot = gameLifecycleService.gameHistory(GameId.fromString(gameId));
            List<GameHistoryEntryHttpResponse> rounds = new ArrayList<>();
            for (int i = 0; i < snapshot.rounds().size(); i++) {
                Game.RoundContext context = snapshot.rounds().get(i);
                RoundResult round = context.round();
                PggnPly ply = PggnWriter.toPly((i / 2) + 1, context);
                rounds.add(new GameHistoryEntryHttpResponse(
                        ply.moveNumber(), round.mover().name(), round.guesser().name(),
                        round.actualMove().from().toAlgebraic(), round.actualMove().to().toAlgebraic(), ply.realSan(),
                        round.guessedMove() == null ? null : round.guessedMove().from().toAlgebraic(),
                        round.guessedMove() == null ? null : round.guessedMove().to().toAlgebraic(),
                        ply.guessedSan(), round.guessedCorrectly(),
                        context.boardAfter() == null ? null : mapper.toBoardCells(context.boardAfter())
                ));
            }
            return ResponseEntity.ok(new GameHistoryHttpResponse(mapper.toBoardCells(snapshot.initialBoard()), rounds));
        } catch (GameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("GAME_NOT_FOUND", "Partie introuvable"));
        }
    }

    private Color resolveColor(String requested) {
        if (requested == null || "RANDOM".equals(requested)) {
            return ThreadLocalRandom.current().nextBoolean() ? Color.WHITE : Color.BLACK;
        }
        return Color.valueOf(requested);
    }
}
