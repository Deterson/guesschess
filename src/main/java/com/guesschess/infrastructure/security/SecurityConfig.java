package com.guesschess.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * L'endpoint WebSocket (/ws/**) reste public/non authentifie : les comptes joueurs
 * restent separes du flux de jeu pour l'instant (etape 4 de la roadmap), le lien
 * compte<->partie est une fonctionnalite future. Seuls les nouveaux endpoints REST du
 * contexte "Compte joueur" (/api/account/**) exigent un JWT Bearer valide. Voir
 * JwtConfig pour les beans JwtEncoder/JwtDecoder (isoles pour eviter une reference
 * circulaire avec OAuthLoginSuccessHandler).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private final OAuthLoginSuccessHandler oAuthLoginSuccessHandler;

    SecurityConfig(OAuthLoginSuccessHandler oAuthLoginSuccessHandler) {
        this.oAuthLoginSuccessHandler = oAuthLoginSuccessHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**", "/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/account/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2.successHandler(oAuthLoginSuccessHandler))
                .oauth2ResourceServer(rs -> rs.jwt(withDefaults()));
        return http.build();
    }
}
