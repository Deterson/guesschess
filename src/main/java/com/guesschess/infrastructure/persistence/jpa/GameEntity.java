package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.game.GameResultCause;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.piece.Color;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Ligne de la table games : quelques colonnes scalaires denormalisees (utiles pour de
 * futures requetes) + l'etat riche complet de l'agregat en JSONB (state). Voir
 * GameJpaMapper pour la conversion depuis/vers Game.
 */
@Entity
@Table(name = "games")
class GameEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_winner")
    private Color resultWinner;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_cause")
    private GameResultCause resultCause;

    @Enumerated(EnumType.STRING)
    @Column(name = "side_to_move", nullable = false)
    private Color sideToMove;

    @Convert(converter = GameStateJsonConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state", columnDefinition = "jsonb", nullable = false)
    private GameStateJson state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GameEntity() {
        // JPA
    }

    GameEntity(UUID id, GameStatus status, Color resultWinner, GameResultCause resultCause,
               Color sideToMove, GameStateJson state, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.status = status;
        this.resultWinner = resultWinner;
        this.resultCause = resultCause;
        this.sideToMove = sideToMove;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    GameStatus getStatus() {
        return status;
    }

    Color getResultWinner() {
        return resultWinner;
    }

    GameResultCause getResultCause() {
        return resultCause;
    }

    Color getSideToMove() {
        return sideToMove;
    }

    GameStateJson getState() {
        return state;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Reecrit les champs mutables depuis l'etat courant du domaine (entite managee :
     * le dirty-checking JPA se charge de repercuter ces changements au flush/commit).
     */
    void updateFrom(GameStatus status, Color resultWinner, GameResultCause resultCause,
                     Color sideToMove, GameStateJson state, Instant updatedAt) {
        this.status = status;
        this.resultWinner = resultWinner;
        this.resultCause = resultCause;
        this.sideToMove = sideToMove;
        this.state = state;
        this.updatedAt = updatedAt;
    }
}
