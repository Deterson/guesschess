package com.guesschess.infrastructure.security;

import com.guesschess.domain.account.AnonymousId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Identite anonyme persistante (etape 6 de la roadmap) : cookie HttpOnly signe par
 * HMAC (reutilise app.jwt.secret, deja obligatoire au demarrage - voir JwtConfig),
 * longue duree (~1 an), regenere uniquement si absent ou invalide - jamais si un
 * compte est deja connecte, cette identite n'est qu'un filet pour le jeu en anonyme.
 * Pose l'AnonymousId resolu en attribut de requete pour
 * AnonymousIdentityHandshakeInterceptor (WebSocket) et tout futur endpoint REST.
 */
@Component
public class AnonymousIdentityFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = "guesschess.anonymousId";

    private static final String COOKIE_NAME = "guesschess_anon";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);

    private final String secret;
    private final boolean secureCookie;

    public AnonymousIdentityFilter(@Value("${app.jwt.secret}") String secret,
                                    @Value("${app.anonymous-cookie.secure:true}") boolean secureCookie) {
        this.secret = secret;
        this.secureCookie = secureCookie;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AnonymousId anonymousId = readValidCookie(request);
        if (anonymousId == null) {
            anonymousId = AnonymousId.random();
            response.addHeader("Set-Cookie", buildCookie(anonymousId).toString());
        }
        request.setAttribute(REQUEST_ATTRIBUTE, anonymousId);
        chain.doFilter(request, response);
    }

    private AnonymousId readValidCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                AnonymousId decoded = decode(cookie.getValue());
                if (decoded != null) {
                    return decoded;
                }
            }
        }
        return null;
    }

    private AnonymousId decode(String value) {
        int separator = value.indexOf('.');
        if (separator <= 0) {
            return null;
        }
        String rawId = value.substring(0, separator);
        String signature = value.substring(separator + 1);
        boolean valid = MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                sign(rawId).getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            return null;
        }
        try {
            return AnonymousId.fromString(rawId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ResponseCookie buildCookie(AnonymousId anonymousId) {
        String rawId = anonymousId.toString();
        String value = rawId + "." + sign(rawId);
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
    }

    private String sign(String rawId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign anonymous identity cookie", e);
        }
    }
}
