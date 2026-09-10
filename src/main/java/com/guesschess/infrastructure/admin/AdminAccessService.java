package com.guesschess.infrastructure.admin;

import com.guesschess.application.account.AccountService;
import com.guesschess.domain.account.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controle d'acces de la page admin (etape 18) : liste d'emails autorises via la
 * variable d'environnement ADMIN_EMAILS (comma-separated), en attendant un mecanisme
 * plus riche (colonne/role en base) si le besoin grandit - voir CLAUDE.md.
 */
@Component
public class AdminAccessService {

    private final AccountService accountService;
    private final Set<String> adminEmails;

    public AdminAccessService(AccountService accountService, @Value("${guesschess.admin.emails:}") String adminEmailsRaw) {
        this.accountService = accountService;
        this.adminEmails = Arrays.stream(adminEmailsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(UserId userId) {
        if (adminEmails.isEmpty()) {
            return false;
        }
        String email = accountService.getById(userId).email();
        return email != null && adminEmails.contains(email.toLowerCase());
    }
}
