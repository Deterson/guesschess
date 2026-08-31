package com.guesschess.infrastructure.websocket;

import com.guesschess.infrastructure.websocket.dto.AckMessage;
import com.guesschess.infrastructure.websocket.dto.CreateGameResponse;
import com.guesschess.infrastructure.websocket.dto.GameStateMessage;
import com.guesschess.infrastructure.websocket.dto.SubmitGuessRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitMoveRequest;
import com.guesschess.infrastructure.websocket.dto.ViewGameRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers
@Import(com.guesschess.support.PostgresTestContainerConfig.class)
@ActiveProfiles("test")
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
    void viewGameReturnsInitialStateWithLegalMovesAndEmptyHistory() throws Exception {
        StompSession session = connect();
        CreateGameResponse game = createGame(session);

        BlockingQueue<GameStateMessage> viewResponses = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/game.state", handlerFor(GameStateMessage.class, viewResponses));
        session.send("/app/games/" + game.gameId() + "/view", "");

        GameStateMessage state = viewResponses.poll(5, TimeUnit.SECONDS);

        assertNotNull(state);
        assertEquals("WHITE", state.sideToMove());
        assertEquals(20, state.legalMoves().size());
        assertTrue(state.moveHistory().isEmpty());
        // /app/games.create lie desormais les deux couleurs a l'identite de la session
        // appelante (voir GameController.createGame), donc la partie est deja complete.
        assertTrue(state.full());
    }

    @Test
    void resolvedRoundAppendsToMoveHistoryAndUpdatesLegalMoves() throws Exception {
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
        assertEquals(1, state.moveHistory().size());
        assertEquals("WHITE", state.moveHistory().get(0).color());
        assertEquals("e4", state.moveHistory().get(0).san());
        assertFalse(state.legalMoves().isEmpty());
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
        assertNull(state.board()[1][4]);
        assertEquals("wP", state.board()[3][4]);
    }

    /**
     * game.create (etape 6+) lie desormais les deux couleurs a l'identite de la session
     * appelante (voir GameController.createGame) - une session STOMP separee resout sa
     * propre identite anonyme fraiche, donc soumettre un coup/devinette depuis une telle
     * session echouerait avec NotYourColorException (voir GameCreationControllerIntegrationTest.
     * historyIncludesAResolvedRoundWithItsGuessAndTheResultingBoard). Seule la session
     * creatrice agit ici ; la session separee ne fait que s'abonner, pour verifier que la
     * diffusion publique atteint bien une connexion qui n'a jamais rien soumis.
     */
    @Test
    void broadcastReachesASeparateConnectionThatOnlySubscribed() throws Exception {
        StompSession creatorSession = connect();
        CreateGameResponse game = createGame(creatorSession);

        StompSession listenerSession = connect();
        BlockingQueue<GameStateMessage> broadcasts = new LinkedBlockingQueue<>();
        listenerSession.subscribe("/topic/games/" + game.gameId(), handlerFor(GameStateMessage.class, broadcasts));

        BlockingQueue<AckMessage> guessAcks = new LinkedBlockingQueue<>();
        creatorSession.subscribe("/user/queue/guess.ack", handlerFor(AckMessage.class, guessAcks));
        creatorSession.send("/app/games/" + game.gameId() + "/guess",
                new SubmitGuessRequest(game.blackToken(), "e2", "e4", null));
        assertNotNull(guessAcks.poll(5, TimeUnit.SECONDS));

        creatorSession.send("/app/games/" + game.gameId() + "/move",
                new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));

        GameStateMessage state = broadcasts.poll(5, TimeUnit.SECONDS);

        assertNotNull(state);
        assertTrue(state.lastRound().guessedCorrectly());
    }

    /**
     * Reproduit le bug de rechargement de page : le mover soumet son coup, une
     * "nouvelle session" (autre connexion, comme un onglet rafraichi) revoit l'etat
     * avec son propre jeton - elle doit retrouver son coup en attente (pour ne pas
     * retenter une soumission que le serveur bloquerait), alors qu'une session sans
     * jeton (spectateur) ou avec le jeton de l'adversaire ne doit jamais l'obtenir.
     */
    @Test
    void viewAfterReconnectExposesMyOwnPendingMoveButNeverToTheOpponentOrASpectator() throws Exception {
        StompSession session = connect();
        CreateGameResponse game = createGame(session);

        BlockingQueue<AckMessage> moveAcks = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/move.ack", handlerFor(AckMessage.class, moveAcks));
        session.send("/app/games/" + game.gameId() + "/move",
                new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));
        assertNotNull(moveAcks.poll(5, TimeUnit.SECONDS));

        StompSession reloadedMoverSession = connect();
        BlockingQueue<GameStateMessage> moverViews = new LinkedBlockingQueue<>();
        reloadedMoverSession.subscribe("/user/queue/game.state", handlerFor(GameStateMessage.class, moverViews));
        reloadedMoverSession.send("/app/games/" + game.gameId() + "/view", new ViewGameRequest(game.whiteToken()));
        GameStateMessage moverState = moverViews.poll(5, TimeUnit.SECONDS);

        assertNotNull(moverState);
        assertTrue(moverState.mySubmission().submitted());
        assertEquals("e2", moverState.mySubmission().from());
        assertEquals("e4", moverState.mySubmission().to());

        StompSession guesserSession = connect();
        BlockingQueue<GameStateMessage> guesserViews = new LinkedBlockingQueue<>();
        guesserSession.subscribe("/user/queue/game.state", handlerFor(GameStateMessage.class, guesserViews));
        guesserSession.send("/app/games/" + game.gameId() + "/view", new ViewGameRequest(game.blackToken()));
        GameStateMessage guesserState = guesserViews.poll(5, TimeUnit.SECONDS);

        assertNotNull(guesserState);
        assertFalse(guesserState.mySubmission().submitted());

        StompSession spectatorSession = connect();
        BlockingQueue<GameStateMessage> spectatorViews = new LinkedBlockingQueue<>();
        spectatorSession.subscribe("/user/queue/game.state", handlerFor(GameStateMessage.class, spectatorViews));
        spectatorSession.send("/app/games/" + game.gameId() + "/view", "");
        GameStateMessage spectatorState = spectatorViews.poll(5, TimeUnit.SECONDS);

        assertNotNull(spectatorState);
        assertFalse(spectatorState.mySubmission().submitted());
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
