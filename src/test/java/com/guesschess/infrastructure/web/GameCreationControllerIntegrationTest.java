package com.guesschess.infrastructure.web;

import com.guesschess.infrastructure.websocket.dto.AckMessage;
import com.guesschess.infrastructure.websocket.dto.CreateGameResponse;
import com.guesschess.infrastructure.websocket.dto.GameStateMessage;
import com.guesschess.infrastructure.websocket.dto.SubmitGuessRequest;
import com.guesschess.infrastructure.websocket.dto.SubmitMoveRequest;
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
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifie de bout en bout la creation de partie avec choix de couleur/identite et
 * l'acceptation d'invitation (etape 7 de la roadmap) : POST /api/games lie
 * immediatement le createur, POST /api/games/{id}/join lie l'adversaire une seule fois
 * (usage unique).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(com.guesschess.support.PostgresTestContainerConfig.class)
@ActiveProfiles("test")
class GameCreationControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void creatingAGameLinksTheCreatorToTheChosenColor() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");

        assertEquals("WHITE", created.get("creatorColor").asString());
        assertNotNull(created.get("creatorToken").asString());
        assertNotNull(created.get("gameId").asString());
    }

    /**
     * join ne prend aucun corps de requete : la couleur revendiquee est deduite du
     * seul siege encore libre (le createur a deja revendique le sien), pas d'un token
     * fourni par l'appelant - voir GameLifecycleService.joinGame(GameId, PlayerRef).
     */
    @Test
    void joiningClaimsTheOnlyOpenColor() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();

        HttpResponse<String> response = postRaw("/api/games/" + gameId + "/join", null);

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("BLACK", body.get("color").asString());
        assertNotNull(body.get("token").asString());
    }

    @Test
    void joiningAGameThatIsAlreadyFullIsRejected() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();
        postRaw("/api/games/" + gameId + "/join", null);

        HttpResponse<String> secondAttempt = postRaw("/api/games/" + gameId + "/join", null);

        assertEquals(409, secondAttempt.statusCode());
    }

    /**
     * Un spectateur deja connecte (abonne a /topic/games/{id} avant que l'adversaire ne
     * rejoigne) doit voir en direct que la partie est complete, sans devoir recharger la
     * page ni refaire d'appel REST - voir GameCreationController.broadcastGameState.
     */
    @Test
    void joiningBroadcastsFullStateToSpectatorsAlreadyWatching() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession spectatorSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        BlockingQueue<GameStateMessage> broadcasts = new LinkedBlockingQueue<>();
        spectatorSession.subscribe("/topic/games/" + gameId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameStateMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                broadcasts.add((GameStateMessage) payload);
            }
        });

        postRaw("/api/games/" + gameId + "/join", null);

        GameStateMessage state = broadcasts.poll(5, TimeUnit.SECONDS);
        assertNotNull(state);
        assertTrue(state.full());
    }

    @Test
    void joiningAnUnknownGameReturnsNotFound() throws Exception {
        HttpResponse<String> response = postRaw("/api/games/" + java.util.UUID.randomUUID() + "/join", null);

        assertEquals(404, response.statusCode());
    }

    /**
     * Un joueur anonyme qui perd l'URL de sa partie (onglet ferme) doit pouvoir la
     * retrouver via /game/{gameId} seul, grace au cookie anonyme persistant qui
     * identifie son navigateur - simule ici avec un client HTTP qui conserve les
     * cookies entre les deux appels, contrairement au client partage de cette classe
     * (dont chaque appel doit rester une identite anonyme fraiche et independante,
     * voir joiningAGameThatIsAlreadyFullIsRejected).
     */
    @Test
    void myAccessRecoversTheTokenFromTheAnonymousCookieAloneAfterLosingTheUrl() throws Exception {
        HttpClient cookieAwareClient = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpRequest createRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}"))
                .build();
        HttpResponse<String> createResponse = cookieAwareClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode created = objectMapper.readTree(createResponse.body());
        String gameId = created.get("gameId").asString();

        HttpRequest myAccessRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/" + gameId + "/my-access"))
                .GET().build();
        HttpResponse<String> response = cookieAwareClient.send(myAccessRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("WHITE", body.get("color").asString());
        assertEquals(created.get("creatorToken").asString(), body.get("token").asString());
    }

    @Test
    void myAccessForAnIdentityNotLinkedToTheGameReturnsNotFound() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/" + gameId + "/my-access"))
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void myAccessForAnUnknownGameReturnsNotFound() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/games/" + java.util.UUID.randomUUID() + "/my-access"))
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    /**
     * Etape 11 de la roadmap : historique detaille rond par rond, lecture seule et
     * accessible sans jeton comme pggn/my-access.
     */
    @Test
    void historyForAnUnknownGameReturnsNotFound() throws Exception {
        HttpResponse<String> response = getRaw("/api/games/" + java.util.UUID.randomUUID() + "/history");

        assertEquals(404, response.statusCode());
    }

    @Test
    void historyReturnsOnlyTheInitialBoardWhenNoRoundHasBeenResolvedYet() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();

        HttpResponse<String> response = getRaw("/api/games/" + gameId + "/history");

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertTrue(body.get("rounds").isEmpty());
        assertEquals("wP", body.get("initialBoard").get(1).get(4).asString());
        assertEquals("bP", body.get("initialBoard").get(6).get(4).asString());
    }

    /**
     * Cree la partie via STOMP /app/games.create (comme StompFlowIntegrationTest),
     * pas via POST /api/games : la creation REST lie immediatement chaque couleur a
     * l'identite (anonyme ou compte) resolue depuis CETTE requete HTTP precise (etape 7),
     * alors qu'une session STOMP de test independante (sans cookie partage) resout sa
     * propre identite anonyme fraiche - agir sur une partie creee en REST depuis une
     * session STOMP separee echoue donc avec NotYourColorException. La creation via
     * /app/games.create ne lie personne a la creation ; le lien ne se pose qu'au premier
     * coup/devinette soumis par couleur (etape 6), ce qui correspond exactement a cette
     * meme session STOMP - aucun conflit d'identite.
     */
    @Test
    void historyIncludesAResolvedRoundWithItsGuessAndTheResultingBoard() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        BlockingQueue<CreateGameResponse> created = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/games.created", handlerFor(CreateGameResponse.class, created));
        session.send("/app/games.create", "");
        CreateGameResponse game = created.poll(5, TimeUnit.SECONDS);
        assertNotNull(game);
        String gameId = game.gameId();

        BlockingQueue<GameStateMessage> broadcasts = new LinkedBlockingQueue<>();
        session.subscribe("/topic/games/" + gameId, handlerFor(GameStateMessage.class, broadcasts));
        BlockingQueue<AckMessage> guessAcks = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/guess.ack", handlerFor(AckMessage.class, guessAcks));

        session.send("/app/games/" + gameId + "/guess", new SubmitGuessRequest(game.blackToken(), "d2", "d4", null));
        assertNotNull(guessAcks.poll(5, TimeUnit.SECONDS));
        session.send("/app/games/" + gameId + "/move", new SubmitMoveRequest(game.whiteToken(), "e2", "e4", null));
        assertNotNull(broadcasts.poll(5, TimeUnit.SECONDS));

        HttpResponse<String> response = getRaw("/api/games/" + gameId + "/history");

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        JsonNode round = body.get("rounds").get(0);
        assertEquals(1, round.get("moveNumber").asInt());
        assertEquals("WHITE", round.get("mover").asString());
        assertEquals("e2", round.get("actualFrom").asString());
        assertEquals("e4", round.get("actualTo").asString());
        assertEquals("e4", round.get("realSan").asString());
        assertEquals("d4", round.get("guessedSan").asString());
        assertEquals(false, round.get("guessedCorrectly").asBoolean());
        assertEquals("wP", round.get("boardAfter").get(3).get(4).asString());
        assertTrue(round.get("boardAfter").get(1).get(4).isNull());
    }

    private JsonNode post(String path, String jsonBody) throws Exception {
        HttpResponse<String> response = postRaw(path, jsonBody);
        assertEquals(201, response.statusCode());
        return objectMapper.readTree(response.body());
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

    private HttpResponse<String> getRaw(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postRaw(String path, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        builder = jsonBody == null
                ? builder.POST(HttpRequest.BodyPublishers.noBody())
                : builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
