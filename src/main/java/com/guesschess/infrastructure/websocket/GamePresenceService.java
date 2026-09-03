package com.guesschess.infrastructure.websocket;

import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presence en direct des joueurs, en memoire uniquement (jamais persistee - a la
 * difference du reste de l'etat d'une partie, voir CLAUDE.md : une reconnexion
 * reconstruit cet etat depuis zero via register, rien n'est jamais perdu qu'un
 * indicateur "en ligne"). Compte les sessions WebSocket actives par (partie, couleur)
 * plutot qu'un simple booleen, pour rester correct si le meme joueur ouvre plusieurs
 * onglets : la couleur ne redevient "deconnectee" que quand le dernier onglet part.
 */
@Component
public class GamePresenceService {

    private record PresenceKey(GameId gameId, Color color) {
    }

    private final Map<String, PresenceKey> sessionKeys = new ConcurrentHashMap<>();
    private final Map<PresenceKey, Set<String>> activeSessions = new ConcurrentHashMap<>();

    /**
     * Enregistre sessionId comme portant color pour gameId (appele a chaque
     * (re)connexion, voir GameController.viewGame - la seule requete que le frontend
     * envoie systematiquement a chaque (re)abonnement).
     *
     * @return true si color vient de passer de deconnectee a connectee (premiere
     * session active pour ce couple) - le seul cas ou l'appelant doit rediffuser
     * /topic/games/{gameId}/players
     */
    public boolean register(String sessionId, GameId gameId, Color color) {
        PresenceKey key = new PresenceKey(gameId, color);
        sessionKeys.put(sessionId, key);
        Set<String> sessions = activeSessions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        boolean wasEmpty = sessions.isEmpty();
        sessions.add(sessionId);
        return wasEmpty;
    }

    /**
     * A appeler a la fermeture d'une session WebSocket (voir GamePresenceDisconnectListener).
     *
     * @return la partie dont une couleur vient de passer connectee -> deconnectee
     * (derniere session active pour ce couple), vide si cette session ne portait
     * aucune presence ou si d'autres onglets de la meme couleur restent actifs
     */
    public Optional<GameId> unregister(String sessionId) {
        PresenceKey key = sessionKeys.remove(sessionId);
        if (key == null) {
            return Optional.empty();
        }
        Set<String> sessions = activeSessions.get(key);
        if (sessions == null) {
            return Optional.empty();
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            activeSessions.remove(key, sessions);
            return Optional.of(key.gameId());
        }
        return Optional.empty();
    }

    public boolean isConnected(GameId gameId, Color color) {
        Set<String> sessions = activeSessions.get(new PresenceKey(gameId, color));
        return sessions != null && !sessions.isEmpty();
    }
}
