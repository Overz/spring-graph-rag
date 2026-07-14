package com.github.overz.api.internal.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API stateless protegida por JWT (RF35/ADL-008): nenhuma rota aberta além das
 * explicitamente liberadas — health probe e scrape de métricas do OTel Collector
 * (observabilidade local; endurecimento de borda/TLS é do Épico 9).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
class SecurityConfig {

  private final CallerContextJwtConverter callerContextJwtConverter;

  @Bean
  SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
    http
      // API stateless com bearer token: sem sessão/cookie, CSRF não se aplica
      .csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt.jwtAuthenticationConverter(callerContextJwtConverter))
      );
    return http.build();
  }

  /**
   * Decoder com clock skew ZERO: plataforma 100% local, sem drift de relógio — a folga
   * default de 60s do Spring Security tornaria "expirado" impreciso (e intestável de forma
   * honesta). Supplier mantém a resolução do JWKS preguiçosa: a app sobe mesmo com o
   * Keycloak ainda inicializando; a primeira requisição autenticada resolve o issuer.
   */
  @Bean
  JwtDecoder jwtDecoder(
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String issuer
  ) {
    final var decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
    return new SupplierJwtDecoder(() -> {
      decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        new JwtIssuerValidator(issuer),
        new JwtTimestampValidator(Duration.ZERO)
      ));
      return decoder;
    });
  }

}
