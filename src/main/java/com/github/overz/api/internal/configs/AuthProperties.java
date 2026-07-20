package com.github.overz.api.internal.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Parâmetros do login por phantom token (RF35, ADR-004), externalizados em {@code app.auth.*}.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
  URI identityProviderAuthUri,
  String identityProviderClientId
) {
}
