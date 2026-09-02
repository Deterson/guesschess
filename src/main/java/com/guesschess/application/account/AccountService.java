package com.guesschess.application.account;

import com.guesschess.domain.account.AccountSettingKey;
import com.guesschess.domain.account.AccountSettingsRepository;
import com.guesschess.domain.account.OAuthIdentity;
import com.guesschess.domain.account.OAuthProvider;
import com.guesschess.domain.account.User;
import com.guesschess.domain.account.UserId;
import com.guesschess.domain.account.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Orchestration du bounded context "Compte joueur" (etape 4 de la roadmap).
 */
@Service
public class AccountService {

    /**
     * 3 a 20 caracteres, lettres/chiffres/tiret/underscore, ne commence pas par un
     * tiret (etape 14 - format tranche avec l'utilisateur).
     */
    private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_-]{2,19}$");
    private static final Set<String> RESERVED_LOGINS = Set.of("anonymous", "anonyme");

    private final UserRepository userRepository;
    private final AccountSettingsRepository accountSettingsRepository;

    public AccountService(UserRepository userRepository, AccountSettingsRepository accountSettingsRepository) {
        this.userRepository = userRepository;
        this.accountSettingsRepository = accountSettingsRepository;
    }

    public Optional<AccountSnapshot> findByOAuthIdentity(OAuthProvider provider, String externalId) {
        return userRepository.findByOAuthIdentity(provider, externalId).map(this::toSnapshot);
    }

    /**
     * Cree le compte (etape 14) - a n'appeler qu'apres verification du pendingToken
     * d'inscription (voir JwtService.decodePendingRegistrationToken) : rien n'est
     * insere en base tant que cette methode n'a pas ete appelee avec un login valide,
     * conformement a la regle "pas de login = pas de compte" pour tout nouveau
     * joueur.
     */
    public AccountSnapshot completeRegistration(OAuthProvider provider, String externalId, String email, String login) {
        String normalizedLogin = validateLogin(login);
        User user = User.newUser(normalizedLogin, email, new OAuthIdentity(provider, externalId));
        userRepository.insert(user);
        return toSnapshot(user);
    }

    /**
     * Pose le login d'un compte historique cree avant l'etape 14 (login nullable en
     * base - voir migration V9) : ce compte existe deja mais reste bloque cote
     * frontend tant qu'il n'est pas passe par ce point une fois.
     */
    public AccountSnapshot setLoginForExistingAccount(UserId id, String login) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no user found for id: " + id));
        if (user.login() != null) {
            throw new IllegalStateException("login is already set and immutable for user: " + id);
        }
        String normalizedLogin = validateLogin(login);
        User updated = new User(user.id(), user.displayName(), normalizedLogin, user.bio(), user.email(), user.identities(), user.createdAt());
        userRepository.update(updated);
        return toSnapshot(updated);
    }

    private String validateLogin(String login) {
        if (login == null) {
            throw new InvalidLoginException("LOGIN_INVALID_FORMAT", "Le pseudo doit contenir entre 3 et 20 caracteres (lettres, chiffres, - ou _)");
        }
        String trimmed = login.trim();
        if (!LOGIN_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidLoginException("LOGIN_INVALID_FORMAT", "Le pseudo doit contenir entre 3 et 20 caracteres (lettres, chiffres, - ou _) et ne peut pas commencer par un tiret");
        }
        if (RESERVED_LOGINS.contains(trimmed.toLowerCase())) {
            throw new InvalidLoginException("LOGIN_RESERVED", "Ce pseudo n'est pas disponible");
        }
        if (userRepository.existsByLoginIgnoreCase(trimmed)) {
            throw new InvalidLoginException("LOGIN_TAKEN", "Ce pseudo est deja pris");
        }
        return trimmed;
    }

    public AccountSnapshot getById(UserId id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no user found for id: " + id));
        return toSnapshot(user);
    }

    /**
     * Nom d'affichage modifiable par l'utilisateur (etape 8), initialise au login a
     * la creation du compte (etape 14) - 2 a 32 caracteres Unicode.
     */
    public AccountSnapshot updateDisplayName(UserId id, String newDisplayName) {
        if (newDisplayName == null || !isLengthWithin(newDisplayName.trim(), 2, 32)) {
            throw new IllegalArgumentException("displayName must be between 2 and 32 characters");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no user found for id: " + id));
        User updated = new User(user.id(), newDisplayName.trim(), user.login(), user.bio(), user.email(), user.identities(), user.createdAt());
        userRepository.update(updated);
        return toSnapshot(updated);
    }

    /**
     * Bio libre du profil (etape 14) - 5000 caracteres Unicode maximum, aucun
     * minimum (chaine vide autorisee, valeur par defaut a la creation du compte).
     */
    public AccountSnapshot updateBio(UserId id, String newBio) {
        String normalized = newBio == null ? "" : newBio;
        if (!isLengthWithin(normalized, 0, 5000)) {
            throw new IllegalArgumentException("bio must be at most 5000 characters");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("no user found for id: " + id));
        User updated = new User(user.id(), user.displayName(), user.login(), normalized, user.email(), user.identities(), user.createdAt());
        userRepository.update(updated);
        return toSnapshot(updated);
    }

    private boolean isLengthWithin(String value, int min, int max) {
        int length = value.codePointCount(0, value.length());
        return length >= min && length <= max;
    }

    private AccountSnapshot toSnapshot(User user) {
        return new AccountSnapshot(user.id(), user.displayName(), user.login(), user.bio(), user.email());
    }

    public AccountSettingsSnapshot getSettings(UserId id) {
        Map<AccountSettingKey, String> settings = accountSettingsRepository.findByUserId(id);
        boolean turnBlinkReminder = Boolean.parseBoolean(settings.getOrDefault(AccountSettingKey.TURN_BLINK_REMINDER, "true"));
        return new AccountSettingsSnapshot(turnBlinkReminder);
    }

    public AccountSettingsSnapshot updateTurnBlinkReminder(UserId id, boolean turnBlinkReminder) {
        accountSettingsRepository.upsert(id, AccountSettingKey.TURN_BLINK_REMINDER, Boolean.toString(turnBlinkReminder));
        return new AccountSettingsSnapshot(turnBlinkReminder);
    }
}
