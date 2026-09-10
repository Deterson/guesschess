package com.guesschess.infrastructure.admin.web;

import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/admin/** (etape 18) : authentification exigee par SecurityConfig, puis
 * verification "est-ce un admin" par AdminController lui-meme (403 sinon) - voir
 * AdminAccessServiceTest pour la logique de comparaison d'email elle-meme.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(PostgresTestContainerConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "guesschess.admin.emails=admin@example.com")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Test
    void usersWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersForANonAdminAccountReturns403() throws Exception {
        AccountSnapshot account = accountService.completeRegistration(
                OAuthProvider.GOOGLE, "admin-test-1", "someone@example.com", "someone-admin-test");

        mockMvc.perform(get("/api/admin/users").with(jwt().jwt(j -> j.subject(account.id().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ADMIN_FORBIDDEN"));
    }

    @Test
    void usersAndGamesForAnAdminAccountReturnsOk() throws Exception {
        AccountSnapshot admin = accountService.completeRegistration(
                OAuthProvider.GOOGLE, "admin-test-2", "admin@example.com", "admin-test-user");

        mockMvc.perform(get("/api/admin/users").with(jwt().jwt(j -> j.subject(admin.id().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.login == 'admin-test-user')]").exists());

        mockMvc.perform(get("/api/admin/games").with(jwt().jwt(j -> j.subject(admin.id().toString()))))
                .andExpect(status().isOk());
    }
}
