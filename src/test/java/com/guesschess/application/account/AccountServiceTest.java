package com.guesschess.application.account;

import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountServiceTest {

    private final InMemoryUserRepository userRepository = new InMemoryUserRepository();
    private final AccountService accountService = new AccountService(userRepository);

    @Test
    void createsANewAccountOnFirstLogin() {
        AccountSnapshot account = accountService.findOrCreateByOAuthIdentity(
                OAuthProvider.GOOGLE, "google-123", "Alice", "alice@example.com");

        assertEquals("Alice", account.displayName());
        assertEquals("alice@example.com", account.email());
        assertEquals(1, userRepository.count());
    }

    @Test
    void findOrCreateIsIdempotentForTheSameIdentity() {
        AccountSnapshot first = accountService.findOrCreateByOAuthIdentity(
                OAuthProvider.GOOGLE, "google-123", "Alice", "alice@example.com");
        AccountSnapshot second = accountService.findOrCreateByOAuthIdentity(
                OAuthProvider.GOOGLE, "google-123", "Alice", "alice@example.com");

        assertEquals(first.id(), second.id());
        assertEquals(1, userRepository.count());
    }

    @Test
    void getByIdReturnsTheCreatedAccount() {
        AccountSnapshot created = accountService.findOrCreateByOAuthIdentity(
                OAuthProvider.GITHUB, "gh-42", "bob", null);

        AccountSnapshot fetched = accountService.getById(created.id());

        assertEquals(created, fetched);
    }

    @Test
    void getByIdThrowsForAnUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> accountService.getById(UserId.random()));
    }

    private static class InMemoryUserRepository implements UserRepository {

        private final Map<UserId, User> byId = new HashMap<>();

        @Override
        public void insert(User user) {
            byId.put(user.id(), user);
        }

        @Override
        public Optional<User> findByOAuthIdentity(OAuthProvider provider, String externalId) {
            return byId.values().stream()
                    .filter(u -> u.identities().stream().anyMatch(i -> i.provider() == provider && i.externalId().equals(externalId)))
                    .findFirst();
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(byId.get(id));
        }

        int count() {
            return byId.size();
        }
    }
}
