package com.guesschess.infrastructure.admin.web;

import com.guesschess.application.account.AccountService;
import com.guesschess.domain.account.UserId;
import com.guesschess.infrastructure.account.persistence.AdminUserQueries;
import com.guesschess.infrastructure.admin.AdminAccessService;
import com.guesschess.infrastructure.persistence.jpa.AdminGameQueries;
import com.guesschess.infrastructure.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Page admin (etape 18) : exploration en lecture seule de tous les comptes et de
 * toutes les parties, reservee aux emails listes dans ADMIN_EMAILS (voir
 * AdminAccessService). /api/admin/** exige deja un JWT valide (SecurityConfig) ; ce
 * controleur ajoute la verification "est-ce un admin", en 403 sinon - pas de route
 * frontend visible pour qui n'y a pas droit (voir CLAUDE.md).
 */
@RestController
@RequestMapping("/api/admin")
class AdminController {

    private final AdminAccessService adminAccessService;
    private final AdminUserQueries adminUserQueries;
    private final AdminGameQueries adminGameQueries;
    private final AccountService accountService;

    AdminController(AdminAccessService adminAccessService, AdminUserQueries adminUserQueries,
                     AdminGameQueries adminGameQueries, AccountService accountService) {
        this.adminAccessService = adminAccessService;
        this.adminUserQueries = adminUserQueries;
        this.adminGameQueries = adminGameQueries;
        this.accountService = accountService;
    }

    @GetMapping("/users")
    ResponseEntity<?> users(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "30") int size,
                             @RequestParam(required = false) String q) {
        UserId userId = UserId.fromString(jwt.getSubject());
        if (!adminAccessService.isAdmin(userId)) {
            return forbidden();
        }
        int boundedSize = Math.clamp(size, 1, 100);
        List<AdminUserHttpResponse> body = adminUserQueries.search(q, Math.max(page, 0), boundedSize).stream()
                .map(row -> new AdminUserHttpResponse(
                        row.id().toString(), row.login(), row.displayName(), row.email(), row.createdAt().toString()))
                .toList();
        return ResponseEntity.ok(body);
    }

    @GetMapping("/games")
    ResponseEntity<?> games(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "30") int size) {
        UserId userId = UserId.fromString(jwt.getSubject());
        if (!adminAccessService.isAdmin(userId)) {
            return forbidden();
        }
        int boundedSize = Math.clamp(size, 1, 100);
        List<AdminGameHttpResponse> body = adminGameQueries.list(Math.max(page, 0), boundedSize).stream()
                .map(row -> new AdminGameHttpResponse(
                        row.id().toString(),
                        row.variant().name(),
                        row.status().name(),
                        row.resultWinner() == null ? null : row.resultWinner().name(),
                        row.resultCause() == null ? null : row.resultCause().name(),
                        label(row.whitePlayerType(), row.whitePlayerId()),
                        type(row.whitePlayerType()),
                        label(row.blackPlayerType(), row.blackPlayerId()),
                        type(row.blackPlayerType()),
                        row.createdAt().toString(),
                        row.updatedAt().toString()))
                .toList();
        return ResponseEntity.ok(body);
    }

    private String type(String playerType) {
        if (playerType == null) {
            return null;
        }
        return playerType.startsWith("COMPUTER_") ? "COMPUTER" : playerType;
    }

    private String label(String playerType, java.util.UUID playerId) {
        if (playerType == null) {
            return null;
        }
        if (playerType.startsWith("COMPUTER_")) {
            return playerType.substring("COMPUTER_".length());
        }
        if ("ACCOUNT".equals(playerType)) {
            return accountService.getById(new UserId(playerId)).login();
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("ADMIN_FORBIDDEN", "Acces reserve aux administrateurs"));
    }
}
