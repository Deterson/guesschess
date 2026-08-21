package com.guesschess.infrastructure.account.web;

import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.UserId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST du contexte "Compte joueur" (etape 4 de la roadmap), proteges par
 * JWT - separes du flux WebSocket/PlayerToken du contexte "Partie".
 */
@RestController
@RequestMapping("/api/account")
class AccountController {

    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
        AccountSnapshot account = accountService.getById(UserId.fromString(jwt.getSubject()));
        return new AccountResponse(account.id().toString(), account.displayName(), account.email());
    }
}
