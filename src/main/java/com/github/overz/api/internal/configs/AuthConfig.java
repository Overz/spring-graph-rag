package com.github.overz.api.internal.configs;

import com.github.overz.api.internal.auth.AuthController;
import com.github.overz.api.internal.auth.KeycloakTokenClient;
import com.github.overz.api.internal.auth.OpaqueTokenGenerator;
import com.github.overz.api.internal.auth.PhantomTokenIssuer;
import com.github.overz.api.internal.auth.PhantomTokenIssuerImpl;
import com.github.overz.api.internal.auth.PhantomTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Wiring do login por phantom token (RF35, ADR-004) — nenhuma classe do fluxo leva estereótipo. */
@Configuration
class AuthConfig {

  @Bean
  KeycloakTokenClient keycloakTokenClient(
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String issuer,
    final AuthProperties properties
  ) {
    return new KeycloakTokenClient(issuer, properties.keycloakClientId());
  }

  @Bean
  PhantomTokenRepository phantomTokenRepository(final StringRedisTemplate redisTemplate) {
    return new PhantomTokenRepository(redisTemplate);
  }

  @Bean
  OpaqueTokenGenerator opaqueTokenGenerator() {
    return new OpaqueTokenGenerator();
  }

  @Bean
  PhantomTokenIssuer phantomTokenIssuer(
    final KeycloakTokenClient keycloakTokenClient,
    final PhantomTokenRepository phantomTokenRepository,
    final OpaqueTokenGenerator opaqueTokenGenerator,
    final JwtDecoder jwtDecoder
  ) {
    return new PhantomTokenIssuerImpl(keycloakTokenClient, phantomTokenRepository, opaqueTokenGenerator, jwtDecoder);
  }

  @Bean
  AuthController authController(final PhantomTokenIssuer phantomTokenIssuer) {
    return new AuthController(phantomTokenIssuer);
  }

}
