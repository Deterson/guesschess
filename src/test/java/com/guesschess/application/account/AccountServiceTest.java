package com.guesschess.application.account;

import com.guesschess.domain.account.AccountSettingKey;
import com.guesschess.domain.account.AccountSettingsRepository;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest {

    private final InMemoryUserRepository userRepository = new InMemoryUserRepository();
    private final InMemoryAccountSettingsRepository accountSettingsRepository = new InMemoryAccountSettingsRepository();
    private final AccountService accountService = new AccountService(userRepository, accountSettingsRepository);

    @Test
    void completeRegistrationCreatesANewAccountWithLoginAsDisplayName() {
        AccountSnapshot account = accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", "alice@example.com", "alice");

        assertEquals("alice", account.login());
        assertEquals("alice", account.displayName());
        assertEquals("", account.bio());
        assertEquals("alice@example.com", account.email());
        assertEquals(1, userRepository.count());
    }

    @Test
    void completeRegistrationRejectsAnInvalidFormat() {
        assertThrows(InvalidLoginException.class,
                () -> accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", null, "-ab"));
        assertThrows(InvalidLoginException.class,
                () -> accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", null, "ab"));
        assertEquals(0, userRepository.count());
    }

    @Test
    void completeRegistrationRejectsReservedLogins() {
        assertThrows(InvalidLoginException.class,
                () -> accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", null, "Anonymous"));
        assertThrows(InvalidLoginException.class,
                () -> accountService.completeRegistration(OAuthProvider.GOOGLE, "google-124", null, "anonyme"));
        assertEquals(0, userRepository.count());
    }

    @Test
    void completeRegistrationRejectsALoginAlreadyTakenIgnoringCase() {
        accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", null, "Alice");

        assertThrows(InvalidLoginException.class,
                () -> accountService.completeRegistration(OAuthProvider.GITHUB, "gh-1", null, "alice"));
        assertEquals(1, userRepository.count());
    }

    @Test
    void findByOAuthIdentityIsEmptyForAnUnknownIdentity() {
        assertTrue(accountService.findByOAuthIdentity(OAuthProvider.GOOGLE, "unknown").isEmpty());
    }

    @Test
    void findByOAuthIdentityFindsTheCreatedAccount() {
        AccountSnapshot created = accountService.completeRegistration(OAuthProvider.GITHUB, "gh-42", null, "bob");

        assertEquals(created, accountService.findByOAuthIdentity(OAuthProvider.GITHUB, "gh-42").orElseThrow());
    }

    @Test
    void setLoginForExistingAccountPosesTheLoginOfALegacyAccount() {
        UserId legacyId = UserId.random();
        userRepository.insert(legacyAccountWithoutLogin(legacyId, "Legacy Display Name"));

        AccountSnapshot updated = accountService.setLoginForExistingAccount(legacyId, "legacyuser");

        assertEquals("legacyuser", updated.login());
        assertEquals("Legacy Display Name", updated.displayName());
    }

    @Test
    void setLoginForExistingAccountRejectsASecondAttempt() {
        UserId legacyId = UserId.random();
        userRepository.insert(legacyAccountWithoutLogin(legacyId, "Legacy Display Name"));
        accountService.setLoginForExistingAccount(legacyId, "legacyuser");

        assertThrows(IllegalStateException.class, () -> accountService.setLoginForExistingAccount(legacyId, "another"));
    }

    @Test
    void getByIdThrowsForAnUnknownId() {
        assertThrows(IllegalArgumentException.class, () -> accountService.getById(UserId.random()));
    }

    @Test
    void updateDisplayNameChangesTheStoredName() {
        AccountSnapshot created = accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", "alice@example.com", "alice");

        AccountSnapshot updated = accountService.updateDisplayName(created.id(), "Alicia");

        assertEquals("Alicia", updated.displayName());
        assertEquals("Alicia", accountService.getById(created.id()).displayName());
    }

    @Test
    void updateDisplayNameRejectsFewerThanTwoCharacters() {
        AccountSnapshot created = accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", "alice@example.com", "alice");

        assertThrows(IllegalArgumentException.class, () -> accountService.updateDisplayName(created.id(), "A"));
    }

    @Test
    void updateBioChangesTheStoredBio() {
        AccountSnapshot created = accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", "alice@example.com", "alice");

        AccountSnapshot updated = accountService.updateBio(created.id(), "Hello, I play chess.");

        assertEquals("Hello, I play chess.", updated.bio());
    }

    @Test
    void updateBioRejectsMoreThanFiveThousandCharacters() {
        AccountSnapshot created = accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", "alice@example.com", "alice");

        assertThrows(IllegalArgumentException.class, () -> accountService.updateBio(created.id(), "a".repeat(5001)));
    }

    @Test
    void turnBlinkReminderDefaultsToTrue() {
        AccountSnapshot created = accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", "alice@example.com", "alice");

        assertTrue(accountService.getSettings(created.id()).turnBlinkReminder());
    }

    @Test
    void updateTurnBlinkReminderChangesTheStoredSetting() {
        AccountSnapshot created = accountService.completeRegistration(OAuthProvider.GOOGLE, "google-123", "alice@example.com", "alice");

        accountService.updateTurnBlinkReminder(created.id(), false);

        assertFalse(accountService.getSettings(created.id()).turnBlinkReminder());
    }

    private User legacyAccountWithoutLogin(UserId id, String displayName) {
        return new User(id, displayName, null, "", null,
                java.util.List.of(new com.guesschess.domain.account.OAuthIdentity(OAuthProvider.GOOGLE, "legacy-" + id)),
                java.time.Instant.now());
    }

    private static class InMemoryUserRepository implements UserRepository {

        private final Map<UserId, User> byId = new HashMap<>();

        @Override
        public void insert(User user) {
            byId.put(user.id(), user);
        }

        @Override
        public void update(User user) {
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

        @Override
        public boolean existsByLoginIgnoreCase(String login) {
            return byId.values().stream().anyMatch(u -> u.login() != null && u.login().equalsIgnoreCase(login));
        }

        int count() {
            return byId.size();
        }
    }

    private static class InMemoryAccountSettingsRepository implements AccountSettingsRepository {

        private final Map<UserId, Map<AccountSettingKey, String>> byUserId = new HashMap<>();

        @Override
        public Map<AccountSettingKey, String> findByUserId(UserId userId) {
            return byUserId.getOrDefault(userId, Map.of());
        }

        @Override
        public void upsert(UserId userId, AccountSettingKey key, String value) {
            byUserId.computeIfAbsent(userId, id -> new HashMap<>()).put(key, value);
        }
    }
}
