package com.guesschess.infrastructure.security;

import com.guesschess.application.GameAccess;
import com.guesschess.application.GameAccessRepository;
import com.guesschess.application.GameLifecycleService;
import com.guesschess.application.PlayerRef;
import com.guesschess.application.PlayerToken;
import com.guesschess.application.account.AccountService;
import com.guesschess.application.account.AccountSnapshot;
import com.guesschess.domain.account.AccountSettingKey;
import com.guesschess.domain.account.AccountSettingsRepository;
import com.guesschess.domain.account.AnonymousId;
import com.guesschess.domain.account.OAuthIdentity;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import com.guesschess.domain.game.GameId;
import com.guesschess.domain.piece.Color;
import com.guesschess.infrastructure.persistence.InMemoryGameRepository;
import com.guesschess.infrastructure.websocket.GameMessageMapper;
import com.guesschess.infrastructure.websocket.PlayersBroadcastService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * onAuthenticationSuccess pilote directement (sans passer par le filtre OAuth2 reel,
 * difficile a simuler sans faux serveur d'autorisation).
 */
class OAuthLoginSuccessHandlerTest {

    private static final String SECRET = "unit-test-jwt-secret-at-least-32-bytes-long";

    @Test
    void redirectsWithAPendingRegistrationTokenAndCreatesNothingForAnUnknownIdentity() throws Exception {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        AccountService accountService = new AccountService(userRepository, new InMemoryAccountSettingsRepository());
        JwtService jwtService = new JwtService(jwtEncoder(), jwtDecoder(), Duration.ofHours(1));
        RecordingGameAccessRepository gameAccessRepository = new RecordingGameAccessRepository();
        OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(
                accountService, jwtService, gameAccessRepository, playersBroadcastService(accountService),
                "http://localhost:5173/oauth-callback");

        MockHttpServletRequest request = new MockHttpServletRequest();
        AnonymousId anonymousId = AnonymousId.random();
        request.setAttribute(AnonymousIdentityFilter.REQUEST_ATTRIBUTE, anonymousId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleToken("google-999", "Dana", "dana@example.com"));

        assertEquals(0, userRepository.count());
        String location = response.getRedirectedUrl();
        assertTrue(location != null && location.startsWith("http://localhost:5173/oauth-callback#pendingToken="));
        // Aucun compte n'existe encore : la fusion identite anonyme -> compte est
        // deferee jusqu'a la creation effective du compte (RegistrationController).
        assertEquals(0, gameAccessRepository.relinkCalls.size());
    }

    @Test
    void redirectsWithASessionTokenAndRelinksForAnExistingAccount() throws Exception {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        AccountService accountService = new AccountService(userRepository, new InMemoryAccountSettingsRepository());
        userRepository.insert(new User(UserId.random(), "dana", "dana", "", "dana@example.com",
                List.of(new OAuthIdentity(OAuthProvider.GOOGLE, "google-999")), Instant.now()));
        JwtService jwtService = new JwtService(jwtEncoder(), jwtDecoder(), Duration.ofHours(1));
        RecordingGameAccessRepository gameAccessRepository = new RecordingGameAccessRepository();
        OAuthLoginSuccessHandler handler = new OAuthLoginSuccessHandler(
                accountService, jwtService, gameAccessRepository, playersBroadcastService(accountService),
                "http://localhost:5173/oauth-callback");

        MockHttpServletRequest request = new MockHttpServletRequest();
        AnonymousId anonymousId = AnonymousId.random();
        request.setAttribute(AnonymousIdentityFilter.REQUEST_ATTRIBUTE, anonymousId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, googleToken("google-999", "Dana", "dana@example.com"));

        assertEquals(1, userRepository.count());
        String location = response.getRedirectedUrl();
        assertTrue(location != null && location.startsWith("http://localhost:5173/oauth-callback#token="));
        assertEquals(1, gameAccessRepository.relinkCalls.size());
        assertEquals(anonymousId, gameAccessRepository.relinkCalls.get(0).from().anonymousId());
    }

    /**
     * relinkCalls (RecordingGameAccessRepository) renvoie toujours une liste vide dans
     * ces tests (voir plus bas) - aucune partie a diffuser, donc les dependances de
     * PlayersBroadcastService (GameLifecycleService, SimpMessagingTemplate) n'ont pas
     * besoin d'etre fonctionnelles au-dela de compiler, seulement non-null.
     */
    private PlayersBroadcastService playersBroadcastService(AccountService accountService) {
        var gameLifecycleService = new GameLifecycleService(new InMemoryGameRepository(), new RecordingGameAccessRepository());
        var messagingTemplate = new SimpMessagingTemplate(new MessageChannel() {
            @Override
            public boolean send(Message<?> message, long timeout) {
                return true;
            }
        });
        return new PlayersBroadcastService(gameLifecycleService, new GameMessageMapper(accountService), messagingTemplate);
    }

    private OAuth2AuthenticationToken googleToken(String sub, String name, String email) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", sub);
        attributes.put("name", name);
        attributes.put("email", email);
        OAuth2User principal = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
    }

    private JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey()));
    }

    private JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(hmacKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private SecretKeySpec hmacKey() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private record RelinkCall(PlayerRef.Anonymous from, PlayerRef.Account to) {
    }

    private static class RecordingGameAccessRepository implements GameAccessRepository {

        private final List<RelinkCall> relinkCalls = new ArrayList<>();

        @Override
        public void save(GameAccess access) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<GameAccess> findByToken(PlayerToken token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<GameAccess> findByGameId(GameId gameId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerRef linkPlayer(GameId gameId, Color color, PlayerRef ref) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GameAccess> findAllByAccount(UserId userId, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GameId> relinkAnonymousToAccount(PlayerRef.Anonymous from, PlayerRef.Account to) {
            relinkCalls.add(new RelinkCall(from, to));
            return List.of();
        }
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
