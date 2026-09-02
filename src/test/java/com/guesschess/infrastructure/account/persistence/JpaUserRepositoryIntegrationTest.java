package com.guesschess.infrastructure.account.persistence;

import com.guesschess.domain.account.OAuthIdentity;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import com.guesschess.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@Import(PostgresTestContainerConfig.class)
@ActiveProfiles("test")
class JpaUserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void insertedUserCanBeFoundByIdAndByOAuthIdentity() {
        OAuthIdentity identity = new OAuthIdentity(OAuthProvider.GOOGLE, "google-abc");
        User user = User.newUser("Alice", "alice@example.com", identity);

        userRepository.insert(user);

        User byId = userRepository.findById(user.id()).orElseThrow();
        User byIdentity = userRepository.findByOAuthIdentity(OAuthProvider.GOOGLE, "google-abc").orElseThrow();

        assertEquals("Alice", byId.displayName());
        assertEquals(user.id(), byIdentity.id());
        assertEquals(1, byIdentity.identities().size());
    }

    @Test
    void findingAnUnknownUserOrIdentityReturnsEmpty() {
        assertTrue(userRepository.findById(UserId.random()).isEmpty());
        assertTrue(userRepository.findByOAuthIdentity(OAuthProvider.GITHUB, "unknown").isEmpty());
    }

    @Test
    void theSameProviderIdentityCannotBeLinkedToTwoUsers() {
        OAuthIdentity identity = new OAuthIdentity(OAuthProvider.GITHUB, "gh-42");
        userRepository.insert(User.newUser("Bob", null, identity));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.insert(User.newUser("Someone else", null, identity)));
    }

    /**
     * Login unique insensible a la casse (etape 14) - repose sur l'index unique
     * lower(login) pose par la migration V9, pas seulement sur une verification
     * applicative.
     */
    @Test
    void existsByLoginIgnoreCaseIgnoresCase() {
        userRepository.insert(User.newUser("UniqueLogin", null, new OAuthIdentity(OAuthProvider.GOOGLE, "google-unique")));

        assertTrue(userRepository.existsByLoginIgnoreCase("uniquelogin"));
        assertTrue(userRepository.existsByLoginIgnoreCase("UNIQUELOGIN"));
        assertTrue(!userRepository.existsByLoginIgnoreCase("someoneelse"));
    }

    @Test
    void loginUniqueIndexRejectsTheSameLoginIgnoringCase() {
        userRepository.insert(User.newUser("CaseLogin", null, new OAuthIdentity(OAuthProvider.GOOGLE, "google-case-1")));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.insert(User.newUser("caselogin", null, new OAuthIdentity(OAuthProvider.GOOGLE, "google-case-2"))));
    }
}
