package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.game.GameResultCause;
import com.guesschess.domain.game.GameStatus;
import com.guesschess.domain.game.GameVariant;
import com.guesschess.domain.piece.Color;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Page admin (etape 18), lecture seule : remplace la lecture directe en base par SSH
 * documentee dans .github/CLAUDE.md. A cote du port GameRepository (concu pour un acces
 * exclusif partie par partie, pas pour lister toutes les parties) plutot que dedans.
 */
@Component
public class AdminGameQueries {

    private final SpringDataGameJpaRepository games;
    private final SpringDataGameAccessJpaRepository access;

    AdminGameQueries(SpringDataGameJpaRepository games, SpringDataGameAccessJpaRepository access) {
        this.games = games;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<Row> list(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        List<GameEntity> pageContent = games.findAll(PageRequest.of(page, size, sort)).getContent();
        List<UUID> ids = pageContent.stream().map(GameEntity::getId).toList();
        Map<UUID, GameAccessEntity> accessById = access.findAllById(ids).stream()
                .collect(Collectors.toMap(GameAccessEntity::getGameId, Function.identity()));
        return pageContent.stream().map(g -> toRow(g, accessById.get(g.getId()))).toList();
    }

    private Row toRow(GameEntity g, GameAccessEntity a) {
        return new Row(
                g.getId(), g.getVariant(), g.getStatus(), g.getResultWinner(), g.getResultCause(),
                a == null ? null : a.getWhitePlayerType(), a == null ? null : a.getWhitePlayerId(),
                a == null ? null : a.getBlackPlayerType(), a == null ? null : a.getBlackPlayerId(),
                g.getCreatedAt(), g.getUpdatedAt());
    }

    public record Row(UUID id, GameVariant variant, GameStatus status, Color resultWinner, GameResultCause resultCause,
                       String whitePlayerType, UUID whitePlayerId, String blackPlayerType, UUID blackPlayerId,
                       Instant createdAt, Instant updatedAt) {
    }
}
