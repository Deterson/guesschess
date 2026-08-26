package com.guesschess.infrastructure.security;

import com.guesschess.domain.account.AnonymousId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifie le cycle emission/validation du cookie d'identite anonyme (etape 6 de la
 * roadmap) : signature HMAC coherente sur un aller-retour, cookie absent/invalide
 * regenere plutot que de faire echouer la requete.
 */
class AnonymousIdentityFilterTest {

    private static final String SET_COOKIE = "Set-Cookie";

    private final AnonymousIdentityFilter filter = new AnonymousIdentityFilter("test-secret-at-least-32-bytes-long!!", false);

    @Test
    void firstRequestWithoutACookieIssuesANewSignedOne() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getCookies()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).addHeader(eq(SET_COOKIE), argThat(header -> header.contains("guesschess_anon=")));
        verify(request).setAttribute(eq(AnonymousIdentityFilter.REQUEST_ATTRIBUTE), any(AnonymousId.class));
        verify(chain).doFilter(request, response);
    }

    @Test
    void aValidPreviouslyIssuedCookieIsAcceptedAsIsWithoutReissuing() throws Exception {
        HttpServletRequest firstRequest = mock(HttpServletRequest.class);
        HttpServletResponse firstResponse = mock(HttpServletResponse.class);
        when(firstRequest.getCookies()).thenReturn(null);
        filter.doFilterInternal(firstRequest, firstResponse, mock(FilterChain.class));

        ArgumentCaptor<String> cookieHeader = ArgumentCaptor.forClass(String.class);
        verify(firstResponse).addHeader(eq(SET_COOKIE), cookieHeader.capture());
        String cookieValue = extractCookieValue(cookieHeader.getValue());

        HttpServletRequest secondRequest = mock(HttpServletRequest.class);
        HttpServletResponse secondResponse = mock(HttpServletResponse.class);
        when(secondRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("guesschess_anon", cookieValue)});

        filter.doFilterInternal(secondRequest, secondResponse, mock(FilterChain.class));

        verify(secondResponse, never()).addHeader(anyString(), anyString());
        ArgumentCaptor<AnonymousId> resolved = ArgumentCaptor.forClass(AnonymousId.class);
        verify(secondRequest).setAttribute(eq(AnonymousIdentityFilter.REQUEST_ATTRIBUTE), resolved.capture());
        assertEquals(cookieValue.split("\\.")[0], resolved.getValue().toString());
    }

    @Test
    void aTamperedCookieIsRejectedAndReplacedWithANewOne() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        String tampered = UUID.randomUUID() + ".not-a-valid-signature";
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("guesschess_anon", tampered)});

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        ArgumentCaptor<AnonymousId> resolved = ArgumentCaptor.forClass(AnonymousId.class);
        verify(request).setAttribute(eq(AnonymousIdentityFilter.REQUEST_ATTRIBUTE), resolved.capture());
        assertNotEquals(tampered.split("\\.")[0], resolved.getValue().toString());
        verify(response).addHeader(eq(SET_COOKIE), argThat(header -> header.contains("guesschess_anon=")));
    }

    private String extractCookieValue(String setCookieHeader) {
        String withoutName = setCookieHeader.substring(setCookieHeader.indexOf('=') + 1);
        return withoutName.substring(0, withoutName.indexOf(';'));
    }
}
