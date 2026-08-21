package com.guesschess.infrastructure.websocket;

import com.guesschess.infrastructure.websocket.dto.AckMessage;
import com.guesschess.infrastructure.websocket.dto.CreateGameResponse;
import com.guesschess.infrastructure.websocket.dto.GameStateMessage;
import com.guesschess.infrastructure.websocket.dto.SubmitGuessRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitMoveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie le cycle de vie complet d'une partie sur le vrai transport STOMP : creation,
 * devinette, coup, resolution du round diffusee publiquement. C'est aussi le seul
 * endroit qui verifie concretement que la devinette n'est jamais publiee avant la
 * resolution (seul /topic est observe ici, jamais le contenu de pendingGuess).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompFlowIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @Test
    void correctGuessCancelsTheMoveAndBroadcastsTheResolutionPublicly() throws Exception {
        StompSession session = connect();
        CreateGameResponse game = createGame(session);

        BlockingQueue<GameStateMessage> broadcasts = new LinkedBlockingQueue<>();
        session.subscribe("/topic/games/" + game.gameId(), handlerFor(GameStateMessage.class, broadcasts));
        BlockingQueue<AckMessage> guessAcks = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/guess.ack", handlerFor(AckMessage.class, guessAcks));

        session.send("/app/games/" + game.gameId() + "/guess",
                new SubmitGuessRequest(game.blackToken(), "e2", "e4", null));
        assertNotNull(guessAcks.poll(5, TimeUnit.SECONDS));

        session.send("/app/games/" + game.gameId() + "/move",
                new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));
        GameStateMessage state = broadcasts.poll(5, TimeUnit.SECONDS);

        assertNotNull(state);
        assertEquals("BLACK", state.sideToMove());
        assertEquals("ONGOING", state.status());
        assertNotNull(state.lastRound());
        assertTrue(state.lastRound().guessedCorrectly());
        assertFalse(state.lastRound().movePlayed());
        assertEquals("wP", state.board()[1][4]);
        assertNull(state.board()[3][4]);
    }

    @Test
    void moveArrivingFirstOnlyAcksPrivatelyUntilTheGuessArrives() throws Exception {
        StompSession session = connect();
        CreateGameResponse game = createGame(session);

        BlockingQueue<GameStateMessage> broadcasts = new LinkedBlockingQueue<>();
        session.subscribe("/topic/games/" + game.gameId(), handlerFor(GameStateMessage.class, broadcasts));
        BlockingQueue<AckMessage> moveAcks = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/move.ack", handlerFor(AckMessage.class, moveAcks));

        session.send("/app/games/" + game.gameId() + "/move",
                new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));
        assertNotNull(moveAcks.poll(5, TimeUnit.SECONDS));
        assertNull(broadcasts.poll(500, TimeUnit.MILLISECONDS));

        session.send("/app/games/" + game.gameId() + "/guess",
                new SubmitGuessRequest(game.blackToken(), null, null, null));
        GameStateMessage state = broadcasts.poll(5, TimeUnit.SECONDS);

        assertNotNull(state);
        assertEquals("BLACK", state.sideToMove());
        assertTrue(state.lastRound().movePlayed());
        assertFalse(state.lastRound().guessedCorrectly());
    }

    @Test
    void incorrectGuessPlaysTheMoveAndBroadcastsIt() throws Exception {
        StompSession session = connect();
        CreateGameResponse game = createGame(session);

        BlockingQueue<GameStateMessage> broadcasts = new LinkedBlockingQueue<>();
        session.subscribe("/topic/games/" + game.gameId(), handlerFor(GameStateMessage.class, broadcasts));
        BlockingQueue<AckMessage> guessAcks = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/guess.ack", handlerFor(AckMessage.class, guessAcks));

        session.send("/app/games/" + game.gameId() + "/guess",
                new SubmitGuessRequest(game.blackToken(), "d2", "d4", null));
        assertNotNull(guessAcks.poll(5, TimeUnit.SECONDS));

        session.send("/app/games/" + game.gameId() + "/move",
                new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));
        GameStateMessage state = broadcasts.poll(5, TimeUnit.SECONDS);

        assertNotNull(state);
        assertEquals("BLACK", state.sideToMove());
        assertFalse(state.lastRound().guessedCorrectly());
        assertTrue(state.lastRound().movePlayed());
        assertNull(state.board()[1][4]);
        assertEquals("wP", state.board()[3][4]);
    }

    @Test
    void broadcastReachesASeparateConnectionThatOnlySubmittedTheMove() throws Exception {
        StompSession creatorSession = connect();
        CreateGameResponse game = createGame(creatorSession);

        StompSession guesserSession = connect();
        BlockingQueue<AckMessage> guessAcks = new LinkedBlockingQueue<>();
        guesserSession.subscribe("/user/queue/guess.ack", handlerFor(AckMessage.class, guessAcks));
        guesserSession.send("/app/games/" + game.gameId() + "/guess",
                new SubmitGuessRequest(game.blackToken(), "e2", "e4", null));
        assertNotNull(guessAcks.poll(5, TimeUnit.SECONDS));

        StompSession moverSession = connect();
        BlockingQueue<GameStateMessage> broadcasts = new LinkedBlockingQueue<>();
        moverSession.subscribe("/topic/games/" + game.gameId(), handlerFor(GameStateMessage.class, broadcasts));
        moverSession.send("/app/games/" + game.gameId() + "/move",
                new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));

        GameStateMessage state = broadcasts.poll(5, TimeUnit.SECONDS);

        assertNotNull(state);
        assertTrue(state.lastRound().guessedCorrectly());
    }

    private CreateGameResponse createGame(StompSession session) throws Exception {
        BlockingQueue<CreateGameResponse> created = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/games.created", handlerFor(CreateGameResponse.class, created));
        session.send("/app/games.create", "");
        CreateGameResponse game = created.poll(5, TimeUnit.SECONDS);
        assertNotNull(game);
        return game;
    }

    private StompSession connect() throws Exception {
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
    }

    private <T> StompFrameHandler handlerFor(Class<T> type, BlockingQueue<T> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(type.cast(payload));
            }
        };
    }
}
