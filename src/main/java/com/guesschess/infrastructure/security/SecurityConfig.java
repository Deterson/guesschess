package com.guesschess.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * L'endpoint WebSocket (/ws/**) reste public/non authentifie : un compte, quand
 * present, y est resolu de facon best-effort (voir JwtStompChannelInterceptor) mais
 * n'est jamais exige - le jeu reste possible en anonyme (etape 6 de la roadmap).
 * Seuls les endpoints REST du contexte "Compte joueur" (/api/account/**) exigent un
 * JWT Bearer valide. Voir JwtConfig pour les beans JwtEncoder/JwtDecoder (isoles pour
 * eviter une reference circulaire avec OAuthLoginSuccessHandler).
 *
 * AnonymousIdentityFilter s'execute avant l'authentification (addFilterBefore) pour
 * que l'identite anonyme resolue soit disponible en attribut de requete des le
 * handshake WebSocket, qui passe par cette meme chaine de filtres (voir
 * AnonymousIdentityHandshakeInterceptor).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private final OAuthLoginSuccessHandler oAuthLoginSuccessHandler;
    private final AnonymousIdentityFilter anonymousIdentityFilter;

    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    SecurityConfig(OAuthLoginSuccessHandler oAuthLoginSuccessHandler, AnonymousIdentityFilter anonymousIdentityFilter) {
        this.oAuthLoginSuccessHandler = oAuthLoginSuccessHandler;
        this.anonymousIdentityFilter = anonymousIdentityFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .addFilterBefore(anonymousIdentityFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/account/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2.successHandler(oAuthLoginSuccessHandler))
                .oauth2ResourceServer(rs -> rs.jwt(withDefaults()));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
