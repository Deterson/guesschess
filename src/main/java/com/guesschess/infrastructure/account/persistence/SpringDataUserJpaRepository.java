package com.guesschess.infrastructure.account.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataUserJpaRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByLoginIgnoreCase(String login);

    Optional<UserEntity> findByLoginIgnoreCase(String login);

    /**
     * Page admin (etape 18) : recherche libre sur login/nom affiche/email, insensible
     * a la casse - aucun index dedie, volume de comptes attendu trop faible pour que ca
     * pese (page interne, pas de trafic utilisateur).
     */
    @Query("select u from UserEntity u where "
            + "lower(u.login) like lower(concat('%', :q, '%')) "
            + "or lower(u.displayName) like lower(concat('%', :q, '%')) "
            + "or lower(u.email) like lower(concat('%', :q, '%'))")
    Page<UserEntity> search(@Param("q") String q, Pageable pageable);
}
