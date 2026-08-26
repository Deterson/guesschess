package com.guesschess.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_access")
class GameAccessEntity {

    @Id
    @Column(name = "game_id")
    private UUID gameId;

    @Column(name = "white_token", nullable = false, unique = true)
    private UUID whiteToken;

    @Column(name = "black_token", nullable = false, unique = true)
    private UUID blackToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "white_player_type")
    private String whitePlayerType;

    @Column(name = "white_player_id")
    private UUID whitePlayerId;

    @Column(name = "black_player_type")
    private String blackPlayerType;

    @Column(name = "black_player_id")
    private UUID blackPlayerId;

    protected GameAccessEntity() {
        // JPA
    }

    GameAccessEntity(UUID gameId, UUID whiteToken, UUID blackToken, Instant createdAt) {
        this.gameId = gameId;
        this.whiteToken = whiteToken;
        this.blackToken = blackToken;
        this.createdAt = createdAt;
    }

    UUID getGameId() {
        return gameId;
    }

    UUID getWhiteToken() {
        return whiteToken;
    }

    UUID getBlackToken() {
        return blackToken;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    String getWhitePlayerType() {
        return whitePlayerType;
    }

    UUID getWhitePlayerId() {
        return whitePlayerId;
    }

    String getBlackPlayerType() {
        return blackPlayerType;
    }

    UUID getBlackPlayerId() {
        return blackPlayerId;
    }
}
