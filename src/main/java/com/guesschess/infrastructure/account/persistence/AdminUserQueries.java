package com.guesschess.infrastructure.account.persistence;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Page admin (etape 18), lecture seule : remplace la lecture directe en base par SSH
 * documentee dans .github/CLAUDE.md. Volontairement a cote du port UserRepository (qui
 * sert le domaine "Compte joueur") plutot que dedans - une recherche paginee tous
 * comptes confondus n'est pas un besoin du domaine, seulement de cette page interne.
 */
@Component
public class AdminUserQueries {

    private final SpringDataUserJpaRepository users;

    AdminUserQueries(SpringDataUserJpaRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Row> search(String q, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        var result = (q == null || q.isBlank()) ? users.findAll(pageRequest) : users.search(q.trim(), pageRequest);
        return result.map(u -> new Row(u.getId(), u.getLogin(), u.getDisplayName(), u.getEmail(), u.getCreatedAt())).getContent();
    }

    public record Row(UUID id, String login, String displayName, String email, Instant createdAt) {
    }
}
