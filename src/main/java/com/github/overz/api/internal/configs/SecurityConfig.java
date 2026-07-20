package com.github.overz.api.internal.configs;

import com.github.overz.api.internal.auth.PhantomTokenIssuer;
import com.github.overz.api.internal.security.CallerContextJwtConverter;
import com.github.overz.api.internal.security.PhantomTokenAuthenticationProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.ProviderManager;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import static com.github.overz.shared.support.SecurityAuthorities.DOCUMENT_UPLOAD;

/**
 * API stateless protegida por JWT (RF35/ADL-008): nenhuma rota aberta além das
 * explicitamente liberadas — health probe e scrape de métricas do OTel Collector
 * (observabilidade local; endurecimento de borda/TLS é do Épico 9). Login de usuário
 * (RF35, ADR-004) aceita JWT direto (service accounts MCP) OU token opaco phantom-token
 * (usuário final) na mesma rota protegida — ver {@code authenticationManagerResolver}.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

  @Bean
  CallerContextJwtConverter callerContextJwtConverter() {
    return new CallerContextJwtConverter();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
    final HttpSecurity http,
    final AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver
  ) throws Exception {
    http
      // API stateless com bearer token: sem sessão/cookie, CSRF não se aplica
      .csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
        // Login não exige credencial prévia — é ele quem produz uma (RF35, ADR-004).
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
        // Roles granulares por operação (ADL-008/RF30): sem a role da rota → 403.
        .requestMatchers(HttpMethod.POST, "/api/v1/documents").hasAuthority(DOCUMENT_UPLOAD)
        .anyRequest().authenticated()
      )
      .oauth2ResourceServer(oauth2 -> oauth2
        .authenticationManagerResolver(authenticationManagerResolver)
      );
    return http.build();
  }

  /**
   * JWT direto (service accounts MCP) e token opaco phantom-token (login de usuário)
   * convergem no mesmo {@code CallerContext} — decide pelo formato do bearer token (JWT
   * tem 2 pontos), não pela rota, seguindo a recomendação oficial do Spring Security para
   * resource servers que aceitam os dois formatos (ADR-004 D2).
   */
  @Bean
  AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver(
    final JwtDecoder jwtDecoder,
    final CallerContextJwtConverter callerContextJwtConverter,
    final PhantomTokenIssuer phantomTokenIssuer
  ) {
    final var jwtProvider = new JwtAuthenticationProvider(jwtDecoder);
    jwtProvider.setJwtAuthenticationConverter(callerContextJwtConverter);
    final AuthenticationManager jwtManager = new ProviderManager(jwtProvider);
    final AuthenticationManager phantomManager =
      new ProviderManager(new PhantomTokenAuthenticationProvider(phantomTokenIssuer));
    final var bearerTokenResolver = new DefaultBearerTokenResolver();

    return request -> isJwt(bearerTokenResolver.resolve(request)) ? jwtManager : phantomManager;
  }

  private static boolean isJwt(final String token) {
    return token != null && token.chars().filter(c -> c == '.').count() == 2;
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
    return new SupplierJwtDecoder(() -> {
      final var decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
      decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        new JwtIssuerValidator(issuer),
        new JwtTimestampValidator(Duration.ZERO)
      ));
      return decoder;
    });
  }

}
