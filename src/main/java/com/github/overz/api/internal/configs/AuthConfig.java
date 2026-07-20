package com.github.overz.api.internal.configs;

import com.github.overz.api.internal.controllers.AuthController;
import com.github.overz.api.internal.repositories.PhantomTokenRedisRepository;
import com.github.overz.api.internal.repositories.PhantomTokenRepository;
import com.github.overz.api.internal.security.PhantomTokenIssuer;
import com.github.overz.api.internal.security.PhantomTokenIssuerImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

/** Wiring do login por phantom token (RF35, ADR-004) — nenhuma classe do fluxo leva estereótipo. */
@Configuration
class AuthConfig {

  @Bean
  PhantomTokenRepository phantomTokenRepository(final StringRedisTemplate redisTemplate) {
    return new PhantomTokenRedisRepository(redisTemplate);
  }

  @Bean
  PhantomTokenIssuer phantomTokenIssuer(
    final AuthProperties properties,
    final PhantomTokenRepository phantomTokenRepository,
    final JwtDecoder jwtDecoder
  ) {
    return new PhantomTokenIssuerImpl(RestClient.create(), properties, phantomTokenRepository, jwtDecoder);
  }

  @Bean
  AuthController authController(final PhantomTokenIssuer phantomTokenIssuer) {
    return new AuthController(phantomTokenIssuer);
  }

}
