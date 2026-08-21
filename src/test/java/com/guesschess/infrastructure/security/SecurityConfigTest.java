package com.guesschess.infrastructure.security;

import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.support.PostgresTestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/account/me exige un JWT Bearer valide, /ws/** reste public (etape 4 de la
 * roadmap - voir SecurityConfig). Le login OAuth2 reel (redirection vers Google/
 * GitHub) n'est pas simule ici (voir OAuthLoginSuccessHandlerTest pour la logique
 * de find-or-create + redirection).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(PostgresTestContainerConfig.class)
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Test
    void meWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/account/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithAValidJwtReturnsTheAuthenticatedAccount() throws Exception {
        AccountSnapshot account = accountService.findOrCreateByOAuthIdentity(
                OAuthProvider.GOOGLE, "security-test-1", "Carol", "carol@example.com");

        mockMvc.perform(get("/api/account/me").with(jwt().jwt(j -> j.subject(account.id().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Carol"))
                .andExpect(jsonPath("$.email").value("carol@example.com"));
    }
}
