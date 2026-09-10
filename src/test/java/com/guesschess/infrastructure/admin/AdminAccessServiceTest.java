package com.guesschess.infrastructure.admin;

import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAccessServiceTest {

    private final AccountService accountService = mock(AccountService.class);
    private final UserId userId = UserId.random();

    @Test
    void grantsAccessWhenAccountEmailIsInTheConfiguredList() {
        AdminAccessService service = new AdminAccessService(accountService, "alice@example.com, bob@example.com");
        when(accountService.getById(userId)).thenReturn(snapshot("bob@example.com"));

        assertTrue(service.isAdmin(userId));
    }

    @Test
    void comparisonIsCaseInsensitiveAndTrimsWhitespace() {
        AdminAccessService service = new AdminAccessService(accountService, " Alice@Example.com ");
        when(accountService.getById(userId)).thenReturn(snapshot("alice@example.com"));

        assertTrue(service.isAdmin(userId));
    }

    @Test
    void deniesAccessWhenAccountEmailIsNotInTheConfiguredList() {
        AdminAccessService service = new AdminAccessService(accountService, "alice@example.com");
        when(accountService.getById(userId)).thenReturn(snapshot("carol@example.com"));

        assertFalse(service.isAdmin(userId));
    }

    @Test
    void deniesAccessWithoutQueryingTheAccountWhenNoAdminEmailIsConfigured() {
        AdminAccessService service = new AdminAccessService(accountService, "");

        assertFalse(service.isAdmin(userId));
        verifyNoInteractions(accountService);
    }

    private AccountSnapshot snapshot(String email) {
        return new AccountSnapshot(userId, "Name", "login", "", email);
    }
}
