package com.guesschess.infrastructure.security;

import com.guesschess.application.account.AccountService;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * onAuthenticationSuccess pilote directement (sans passer par le filtre OAuth2 reel,
 * difficile a simuler sans faux serveur d'autorisation) : verifie que le compte est
 * cree et que la redirection porte bien un token en fragment d'URL.
 */
class OAuthLoginSuccessHandlerTest {

    @Test
    void createsTheAccountAndRedirectsWithATokenInTheFragment() throws Exception {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        AccountService accountService = new AccountService(userRepository);
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("unit-test-jwt-secret-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtService jwtService = new JwtService(jwtEncoder, Duration.ofHours(1));
        OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(accountService, jwtService, "http://localhost:5173/oauth-callback");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-999");
        attributes.put("name", "Dana");
        attributes.put("email", "dana@example.com");
        OAuth2User principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, token);

        assertEquals(1, userRepository.count());
        String location = response.getRedirectedUrl();
        assertTrue(location != null && location.startsWith("http://localhost:5173/oauth-callback#token="));
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
