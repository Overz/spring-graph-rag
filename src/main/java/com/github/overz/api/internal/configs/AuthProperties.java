package com.github.overz.api.internal.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parâmetros do login por phantom token (RF35, ADR-004), externalizados em {@code app.auth.*}. */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String keycloakClientId) {
}
