package com.guesschess.infrastructure.websocket;

import com.guesschess.application.CreatedGame;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameNotFullException;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.GameView;
import com.guesschess.application.MoveIntent;
import com.guesschess.application.NoOpenColorException;
import com.guesschess.application.NoSuchLegalMoveException;
import com.guesschess.application.NotYourColorException;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.UnknownPlayerTokenException;
import com.guesschess.application.WrongTurnException;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.websocket.dto.AckMessage;
import com.guesschess.infrastructure.websocket.dto.ChatMessage;
import com.guesschess.infrastructure.websocket.dto.CreateGameRequest;
import com.guesschess.infrastructure.websocket.dto.CreateGameResponse;
import com.guesschess.infrastructure.websocket.dto.DrawOfferRequest;
import com.guesschess.infrastructure.websocket.dto.DrawResponseRequest;
import com.guesschess.infrastructure.websocket.dto.ErrorMessage;
import com.guesschess.infrastructure.websocket.dto.GameStateMessage;
import com.guesschess.infrastructure.websocket.dto.RematchOfferRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitChatMessageRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitGuessRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitMoveRequest;
import com.guesschess.infrastructure.websocket.dto.ViewGameRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Optional;

/**
 * Endpoints STOMP du cycle de vie d'une partie. Un round attend obligatoirement les
 * deux soumissions (coup reel + devinette, celle-ci pouvant etre "aucune") avant de
 * se resoudre : quelle que soit celle des deux qui arrive en second, elle declenche
 * la diffusion publique de l'etat resultant sur /topic ; celle qui arrive en premier
 * ne recoit qu'un accuse prive, le round n'etant pas encore resolu.
 */
@Controller
public class GameController {

    private static final int MAX_CHAT_MESSAGE_LENGTH = 500;

    private final GameLifecycleService gameLifecycleService;
    private final GameMessageMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final GamePresenceService presenceService;
    private final PlayersBroadcastService playersBroadcastService;
    private final GameBroadcastService gameBroadcastService;

    public GameController(GameLifecycleService gameLifecycleService, GameMessageMapper mapper,
                           SimpMessagingTemplate messagingTemplate, GamePresenceService presenceService,
                           PlayersBroadcastService playersBroadcastService, GameBroadcastService gameBroadcastService) {
        this.gameLifecycleService = gameLifecycleService;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
        this.playersBroadcastService = playersBroadcastService;
        this.gameBroadcastService = gameBroadcastService;
    }

    /**
     * request (et son eventuel identifiant de variante) reste le seul parametre metier ;
     * simpSessionAttributes sert uniquement a lier les deux couleurs a l'identite de
     * CETTE connexion (compte ou anonyme) des la creation. Necessaire depuis que
     * submitMove/submitGuess exigent une partie complete (GameLifecycleService.
     * requireFull) : ce endpoint reste un raccourci de "partie contre soi-meme" reserve
     * aux tests d'integration (le frontend reel cree via POST /api/games, qui ne lie
     * que le createur, puis attend un /join distinct - voir GameCreationController).
     */
    @MessageMapping("/games.create")
    @SendToUser("/queue/games.created")
    public CreateGameResponse createGame(@Payload(required = false) CreateGameRequest request,
                                          @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {
        GameVariant variant = request == null || request.variant() == null
                ? GameVariant.GUESSCHESS
                : GameVariant.valueOf(request.variant());
        PlayerRef requester = WebSocketPlayerIdentity.resolve(sessionAttributes);
        CreatedGame created = requester == null
                ? gameLifecycleService.createGame(variant)
                : gameLifecycleService.createGame(variant, Color.WHITE, requester);
        if (requester != null) {
            gameLifecycleService.joinGame(created.gameId(), requester);
        }
        return new CreateGameResponse(
                created.gameId().toString(),
                created.whiteToken().toString(),
                created.blackToken().toString(),
                created.variant().name());
    }

    /**
     * request (et son token) est optionnel pour rester compatible avec un client qui
     * n'envoie encore aucun payload - dans ce cas comme si token etait absent,
     * degrade en spectateur (voir GameLifecycleService.viewGame(GameId, PlayerToken)).
     * token identifie le demandeur pour joindre a l'etat public sa propre soumission
     * en attente pour le round en cours (MySubmissionMessage) - jamais celle de
     * l'adversaire : c'est ce qui permet au frontend d'eviter de retenter, apres un
     * rechargement de page, une soumission que le serveur bloquerait.
     *
     * C'est aussi le seul message que le frontend envoie systematiquement a chaque
     * (re)connexion (voir game.ts ensureSubscribed) : point d'accroche naturel pour
     * enregistrer la presence de cette session (GamePresenceService), rediffusee sur
     * /topic/games/{gameId}/players uniquement si cette couleur vient de passer
     * connectee (evite une rediffusion a chaque simple resynchronisation d'etat).
     */
    @MessageMapping("/games/{gameId}/view")
    @SendToUser("/queue/game.state")
    public GameStateMessage viewGame(@DestinationVariable String gameId, @Payload(required = false) ViewGameRequest request,
                                      @Header("simpSessionId") String sessionId) {
        GameId id = GameId.fromString(gameId);
        PlayerToken token = request == null || request.token() == null ? null : PlayerToken.fromString(request.token());
        GameView view = gameLifecycleService.viewGame(id, token);
        gameLifecycleService.resolveColor(id, token).ifPresent(color -> {
            if (presenceService.register(sessionId, id, color)) {
                playersBroadcastService.broadcastPlayers(id);
            }
        });
        return mapper.toGameStateMessage(view.snapshot(), gameLifecycleService.isFull(id), view.mySubmission());
    }

    @MessageMapping("/games/{gameId}/move")
    public void submitMove(@DestinationVariable String gameId, @Payload SubmitMoveRequest request,
                            @Header("simpSessionId") String sessionId,
                            @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {
        MoveIntent intent = mapper.toMoveIntent(request.from(), request.to(), request.promotion());
        PlayerRef requester = WebSocketPlayerIdentity.resolve(sessionAttributes);
        Optional<GameSnapshot> resolved = gameLifecycleService.submitMove(PlayerToken.fromString(request.token()), intent, requester);
        if (resolved.isPresent()) {
            gameBroadcastService.broadcast(resolved.get());
        } else {
            sendToUser(sessionId, "/queue/move.ack", new AckMessage("recorded_waiting_for_guess"));
            // Le round n'est pas resolu (la devinette n'est pas encore arrivee), mais en
            // partie chronometree ce coup vient d'arreter la pendule du joueur au trait et
            // de demarrer celle du devineur (voir Game.submitMove) - un changement public
            // et sans risque anti-triche (GameSnapshot ne revele jamais le coup en attente)
            // qu'il faut donc diffuser ici, hors du seul chemin de resolution de round.
            GameSnapshot snapshot = gameLifecycleService.viewGame(GameId.fromString(gameId));
            if (snapshot.timeControl() != null) {
                gameBroadcastService.broadcast(snapshot);
            }
        }
    }

    @MessageMapping("/games/{gameId}/guess")
    public void submitGuess(@DestinationVariable String gameId, @Payload SubmitGuessRequest request,
                             @Header("simpSessionId") String sessionId,
                             @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {
        MoveIntent intent = request.from() == null || request.to() == null
                ? null
                : mapper.toMoveIntent(request.from(), request.to(), request.promotion());
        PlayerRef requester = WebSocketPlayerIdentity.resolve(sessionAttributes);
        Optional<GameSnapshot> resolved = gameLifecycleService.submitGuess(PlayerToken.fromString(request.token()), intent, requester);
        if (resolved.isPresent()) {
            gameBroadcastService.broadcast(resolved.get());
        } else {
            sendToUser(sessionId, "/queue/guess.ack", new AckMessage("recorded_waiting_for_move"));
        }
    }

    /**
     * Propose la nulle - valable a tout moment (pas seulement au trait), rejetee par
     * le domaine (IllegalStateException, voir handleError) si une offre est deja en
     * attente. Toujours resolue immediatement (pas de paire de soumissions a
     * attendre comme move/guess), donc toujours diffusee.
     */
    @MessageMapping("/games/{gameId}/draw-offer")
    public void offerDraw(@DestinationVariable String gameId, @Payload DrawOfferRequest request,
                           @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {
        PlayerRef requester = WebSocketPlayerIdentity.resolve(sessionAttributes);
        GameSnapshot resolved = gameLifecycleService.offerDraw(PlayerToken.fromString(request.token()), requester);
        gameBroadcastService.broadcast(resolved);
    }

    /**
     * Reponse (acceptation ou refus) a l'offre de nulle en attente - voir offerDraw.
     */
    @MessageMapping("/games/{gameId}/draw-response")
    public void respondToDraw(@DestinationVariable String gameId, @Payload DrawResponseRequest request,
                               @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {
        PlayerRef requester = WebSocketPlayerIdentity.resolve(sessionAttributes);
        GameSnapshot resolved = gameLifecycleService.respondToDraw(PlayerToken.fromString(request.token()), request.accept(), requester);
        gameBroadcastService.broadcast(resolved);
    }

    /**
     * Propose une revanche - valable uniquement une fois la partie FINISHED (rejetee
     * par le domaine sinon, voir handleError). Un seul et meme endpoint sert aussi bien
     * a proposer qu'a accepter : le frontend l'appelle inconditionnellement au clic
     * (voir GameView.vue), l'acceptation etant simplement le fait que l'AUTRE couleur
     * l'appelle a son tour (GameLifecycleService.offerRematch le detecte et cree alors
     * la nouvelle partie, dont l'id est diffuse via GameStateMessage.rematchGameId).
     */
    @MessageMapping("/games/{gameId}/rematch-offer")
    public void offerRematch(@DestinationVariable String gameId, @Payload RematchOfferRequest request,
                              @Header(value = "simpSessionAttributes", required = false) Map<String, Object> sessionAttributes) {
        PlayerRef requester = WebSocketPlayerIdentity.resolve(sessionAttributes);
        GameSnapshot resolved = gameLifecycleService.offerRematch(PlayerToken.fromString(request.token()), requester);
        gameBroadcastService.broadcast(resolved);
    }

    /**
     * Chat ephemere : relaye tel quel sur /topic/games/{gameId}/chat, jamais persiste
     * ni journalise cote serveur (voir ChatMessage). Reserve aux deux joueurs - le
     * jeton doit resoudre une couleur pour CETTE partie (resolveColor, lecture seule,
     * ne lie ni ne modifie rien contrairement a submitMove/submitGuess) ; un
     * spectateur (pas de jeton, ou jeton d'une autre partie) est rejete.
     */
    @MessageMapping("/games/{gameId}/chat")
    public void submitChat(@DestinationVariable String gameId, @Payload SubmitChatMessageRequest request) {
        GameId id = GameId.fromString(gameId);
        PlayerToken token = request.token() == null || request.token().isBlank()
                ? null
                : PlayerToken.fromString(request.token());
        Color color = gameLifecycleService.resolveColor(id, token)
                .orElseThrow(() -> new UnknownPlayerTokenException(token));
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty() || text.length() > MAX_CHAT_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("chat message must be between 1 and " + MAX_CHAT_MESSAGE_LENGTH + " characters");
        }
        messagingTemplate.convertAndSend("/topic/games/" + gameId + "/chat", new ChatMessage(color.name(), text));
    }

    @MessageExceptionHandler({
            UnknownPlayerTokenException.class,
            WrongTurnException.class,
            NotYourColorException.class,
            NoOpenColorException.class,
            GameNotFullException.class,
            NoSuchLegalMoveException.class,
            GameNotFoundException.class,
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    @SendToUser("/queue/errors")
    public ErrorMessage handleError(Exception exception) {
        return new ErrorMessage(exception.getClass().getSimpleName(), exception.getMessage());
    }

    /**
     * Envoie un message prive a l'expediteur du message en cours de traitement.
     * Sans comptes joueurs, il n'y a pas de Principal authentifie : l'id de session
     * WebSocket sert directement de "user" pour le routage /user, comme documente
     * par Spring pour les sessions anonymes.
     */
    private void sendToUser(String sessionId, String destination, Object payload) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        messagingTemplate.convertAndSendToUser(sessionId, destination, payload, headerAccessor.getMessageHeaders());
    }
}
