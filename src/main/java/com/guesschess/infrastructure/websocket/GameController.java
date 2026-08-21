package com.guesschess.infrastructure.websocket;

import com.guesschess.application.CreatedGame;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.GameSnapshot;
import com.guesschess.application.MoveIntent;
import com.guesschess.application.NoSuchLegalMoveException;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.UnknownPlayerTokenException;
import com.guesschess.application.WrongTurnException;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.infrastructure.websocket.dto.CreateGameResponse;
import com.guesschess.infrastructure.websocket.dto.ErrorMessage;
import com.guesschess.infrastructure.websocket.dto.GuessAckMessage;
import com.guesschess.infrastructure.websocket.dto.SubmitGuessRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitMoveRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Endpoints STOMP du cycle de vie d'une partie. Le coup soumis declenche une
 * diffusion publique de l'etat resultant (le round vient de se resoudre) ; la
 * devinette ne declenche qu'un accuse prive, jamais de diffusion publique.
 */
@Controller
public class GameController {

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
    public CreateGameResponse createGame() {
        CreatedGame created = gameLifecycleService.createGame();
        return new CreateGameResponse(
                created.gameId().toString(),
                created.whiteToken().toString(),
                created.blackToken().toString());
    }

    @MessageMapping("/games/{gameId}/move")
    public void submitMove(@DestinationVariable String gameId, @Payload SubmitMoveRequest request) {
        MoveIntent intent = mapper.toMoveIntent(request.from(), request.to(), request.promotion());
        GameSnapshot snapshot = gameLifecycleService.submitMove(PlayerToken.fromString(request.token()), intent);
        messagingTemplate.convertAndSend(
                "/topic/games/" + snapshot.id(),
                mapper.toGameStateMessage(snapshot));
    }

    @MessageMapping("/games/{gameId}/guess")
    @SendToUser("/queue/guess.ack")
    public GuessAckMessage submitGuess(@DestinationVariable String gameId, @Payload SubmitGuessRequest request) {
        MoveIntent intent = request.from() == null || request.to() == null
                ? null
                : mapper.toMoveIntent(request.from(), request.to(), request.promotion());
        gameLifecycleService.submitGuess(PlayerToken.fromString(request.token()), intent);
        return new GuessAckMessage("recorded");
    }

    @MessageExceptionHandler({
            UnknownPlayerTokenException.class,
            WrongTurnException.class,
            NoSuchLegalMoveException.class,
            GameNotFoundException.class,
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    @SendToUser("/queue/errors")
    public ErrorMessage handleError(Exception exception) {
        return new ErrorMessage(exception.getClass().getSimpleName(), exception.getMessage());
    }
}
