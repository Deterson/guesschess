package com.guesschess.infrastructure.persistence.jpa;

import com.guesschess.domain.game.Game;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.game.GameNotFoundException;
import com.guesschess.domain.game.GameRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

/**
 * Implementation Postgres/JPA du port GameRepository (etape 4 de la roadmap),
 * remplace InMemoryGameRepository. withGame garantit le meme acces exclusif par
 * partie que la version en memoire, mais via un verrou base (voir
 * SpringDataGameJpaRepository.findByIdForUpdate) plutot qu'un verrou JVM.
 *
 * @Component plutot que @Repository : ce dernier active la traduction automatique
 * d'exceptions de Spring (PersistenceExceptionTranslationPostProcessor), qui
 * intercepte AUSSI (et donc denature) les IllegalStateException/IllegalArgumentException
 * deliberement levees ici pour respecter le contrat du port GameRepository.
 */
@Component
class JpaGameRepository implements GameRepository {

    private final SpringDataGameJpaRepository springDataRepository;
    private final GameJpaMapper mapper;
    private final EntityManager entityManager;

    JpaGameRepository(SpringDataGameJpaRepository springDataRepository, GameJpaMapper mapper, EntityManager entityManager) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insert(Game game) {
        // Verifier avant d'inserer plutot que de rattraper une violation de
        // contrainte au flush : une fois le flush en echec, la session Hibernate
        // n'est plus utilisable pour le reste de la transaction, ce qui rend la
        // conversion d'exception a cet endroit peu fiable. Fenetre de course
        // theorique acceptable ici (GameId est un UUID aleatoire).
        if (springDataRepository.existsById(game.id().value())) {
            throw new IllegalStateException("a game already exists for id: " + game.id());
        }
        entityManager.persist(mapper.toNewEntity(game));
    }

    @Override
    @Transactional
    public <T> T withGame(GameId id, Function<Game, T> action) {
        GameEntity entity = springDataRepository.findByIdForUpdate(id.value())
                .orElseThrow(() -> new GameNotFoundException(id));
        Game game = mapper.toDomain(entity);
        T result = action.apply(game);
        // Le domaine n'est pas lui-meme une entite JPA : pas de dirty-checking
        // automatique, il faut reecrire explicitement l'etat mute avant la fin de
        // la transaction (l'entite, elle, est managee : ce updateEntity() suffit,
        // la persistance effective se fait au flush/commit).
        mapper.updateEntity(entity, game);
        return result;
    }
}
