package com.guesschess.infrastructure.websocket;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.PlayerRef;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.security.JwtService;
import com.guesschess.infrastructure.websocket.dto.AckMessage;
import com.guesschess.infrastructure.websocket.dto.CreateGameResponse;
import com.guesschess.infrastructure.websocket.dto.SubmitGuessRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitMoveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifie de bout en bout la resolution d'identite a la connexion WebSocket (etape 6
 * de la roadmap) : identite anonyme via le cookie HttpOnly (AnonymousIdentityFilter +
 * AnonymousIdentityHandshakeInterceptor) et compte via un JWT porte en header
 * "Authorization" du CONNECT STOMP (JwtStompChannelInterceptor), toutes deux
 * effectivement liees au GameAccess persiste par le premier coup/devinette soumis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(com.guesschess.support.PostgresTestContainerConfig.class)
@ActiveProfiles("test")
class PlayerIdentityStompIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GameAccessRepository gameAccessRepository;

    @Autowired
    private JwtService jwtService;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @Test
    void anonymousCookieIssuedOverHttpGetsLinkedToTheColorThatActsFirst() throws Exception {
        String cookieHeader = fetchAnonymousCookie();
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add("Cookie", cookieHeader);

        StompSession session = connect(handshakeHeaders, new StompHeaders());
        CreateGameResponse game = createGame(session);
        submitOpeningRound(session, game);

        GameAccess access = gameAccessRepository.findByGameId(GameId.fromString(game.gameId())).orElseThrow();
        assertInstanceOf(PlayerRef.Anonymous.class, access.playerOf(Color.WHITE));
    }

    @Test
    void jwtBearerOnConnectGetsLinkedAsTheAccountForTheColorThatActsFirst() throws Exception {
        UserId userId = UserId.random();
        String jwt = jwtService.generateToken(userId, "Tester");
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

        StompSession session = connect(new WebSocketHttpHeaders(), connectHeaders);
        CreateGameResponse game = createGame(session);
        submitOpeningRound(session, game);

        GameAccess access = gameAccessRepository.findByGameId(GameId.fromString(game.gameId())).orElseThrow();
        assertEquals(new PlayerRef.Account(userId), access.playerOf(Color.WHITE));
    }

    @Test
    void anInvalidBearerTokenFallsBackToAnonymousRatherThanBreakingTheConnection() throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer not-a-real-jwt");

        StompSession session = connect(new WebSocketHttpHeaders(), connectHeaders);
        CreateGameResponse game = createGame(session);
        submitOpeningRound(session, game);

        GameAccess access = gameAccessRepository.findByGameId(GameId.fromString(game.gameId())).orElseThrow();
        assertInstanceOf(PlayerRef.Anonymous.class, access.playerOf(Color.WHITE));
    }

    /**
     * White soumet son coup en premier (round non resolu, juste un ack prive) : assez
     * pour declencher le lien cote White sans avoir besoin de resoudre le round.
     */
    private void submitOpeningRound(StompSession session, CreateGameResponse game) throws Exception {
        BlockingQueue<AckMessage> moveAcks = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/move.ack", handlerFor(AckMessage.class, moveAcks));
        session.send("/app/games/" + game.gameId() + "/move",
                new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));
        assertNotNull(moveAcks.poll(5, TimeUnit.SECONDS));
    }

    private String fetchAnonymousCookie() throws Exception {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/account/me")).GET().build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private CreateGameResponse createGame(StompSession session) throws Exception {
        BlockingQueue<CreateGameResponse> created = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/games.created", handlerFor(CreateGameResponse.class, created));
        session.send("/app/games.create", "");
        CreateGameResponse game = created.poll(5, TimeUnit.SECONDS);
        assertNotNull(game);
        return game;
    }

    private StompSession connect(WebSocketHttpHeaders handshakeHeaders, StompHeaders connectHeaders) throws Exception {
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", handshakeHeaders, connectHeaders,
                new StompSessionHandlerAdapter() {
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
