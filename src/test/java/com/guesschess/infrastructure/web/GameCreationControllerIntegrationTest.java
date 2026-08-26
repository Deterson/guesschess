package com.guesschess.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void creatingAGameLinksTheCreatorToTheChosenColorAndReturnsAnOpponentInvitation() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");

        assertEquals("WHITE", created.get("creatorColor").asString());
        assertEquals("BLACK", created.get("opponentColor").asString());
        assertNotEquals(created.get("creatorToken").asString(), created.get("opponentToken").asString());
        assertNotNull(created.get("gameId").asString());
    }

    @Test
    void joiningWithTheInvitationTokenLinksTheOpponent() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();
        String opponentToken = created.get("opponentToken").asString();

        HttpResponse<String> response = postRaw("/api/games/" + gameId + "/join",
                "{\"token\":\"" + opponentToken + "\"}");

        assertEquals(200, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals("BLACK", body.get("color").asString());
    }

    @Test
    void joiningTheSameInvitationTwiceIsRejectedTheSecondTime() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();
        String opponentToken = created.get("opponentToken").asString();
        postRaw("/api/games/" + gameId + "/join", "{\"token\":\"" + opponentToken + "\"}");

        HttpResponse<String> secondAttempt = postRaw("/api/games/" + gameId + "/join",
                "{\"token\":\"" + opponentToken + "\"}");

        assertEquals(409, secondAttempt.statusCode());
    }

    @Test
    void joiningWithAnUnknownTokenReturnsNotFound() throws Exception {
        JsonNode created = post("/api/games", "{\"variant\":\"GUESSCHESS\",\"color\":\"WHITE\"}");
        String gameId = created.get("gameId").asString();

        HttpResponse<String> response = postRaw("/api/games/" + gameId + "/join",
                "{\"token\":\"" + java.util.UUID.randomUUID() + "\"}");

        assertEquals(404, response.statusCode());
    }

    private JsonNode post(String path, String jsonBody) throws Exception {
        HttpResponse<String> response = postRaw(path, jsonBody);
        assertEquals(201, response.statusCode());
        return objectMapper.readTree(response.body());
    }

    private HttpResponse<String> postRaw(String path, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
