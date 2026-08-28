package com.guesschess.infrastructure.websocket;

import com.guesschess.application.CreatedGame;
import com.guesschess.application.GameLifecycleService;
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
import com.guesschess.infrastructure.websocket.dto.ErrorMessage;
import com.guesschess.infrastructure.websocket.dto.GameStateMessage;
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

    public GameController(GameLifecycleService gameLifecycleService, GameMessageMapper mapper,
                           SimpMessagingTemplate messagingTemplate) {
        this.gameLifecycleService = gameLifecycleService;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/games.create")
    @SendToUser("/queue/games.created")
    public CreateGameResponse createGame(@Payload(required = false) CreateGameRequest request) {
        GameVariant variant = request == null || request.variant() == null
                ? GameVariant.REGULAR
                : GameVariant.valueOf(request.variant());
        CreatedGame created = gameLifecycleService.createGame(variant);
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
     */
    @MessageMapping("/games/{gameId}/view")
    @SendToUser("/queue/game.state")
    public GameStateMessage viewGame(@DestinationVariable String gameId, @Payload(required = false) ViewGameRequest request) {
        GameId id = GameId.fromString(gameId);
        PlayerToken token = request == null || request.token() == null ? null : PlayerToken.fromString(request.token());
        GameView view = gameLifecycleService.viewGame(id, token);
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
            broadcast(resolved.get());
        } else {
            sendToUser(sessionId, "/queue/move.ack", new AckMessage("recorded_waiting_for_guess"));
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
            broadcast(resolved.get());
        } else {
            sendToUser(sessionId, "/queue/guess.ack", new AckMessage("recorded_waiting_for_move"));
        }
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
            NoSuchLegalMoveException.class,
            GameNotFoundException.class,
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    @SendToUser("/queue/errors")
    public ErrorMessage handleError(Exception exception) {
        return new ErrorMessage(exception.getClass().getSimpleName(), exception.getMessage());
    }

    private void broadcast(GameSnapshot snapshot) {
        boolean full = gameLifecycleService.isFull(snapshot.id());
        messagingTemplate.convertAndSend("/topic/games/" + snapshot.id(), mapper.toGameStateMessage(snapshot, full));
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
