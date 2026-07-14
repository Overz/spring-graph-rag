package com.github.overz.bdd;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obtém JWTs reais do Keycloak de teste via password grant dos usuários seedados, com
 * cache por usuário na execução da suíte (SDD qualidade-e-testes §3 — mitigação do risco
 * R13: evita centenas de round-trips ao Keycloak).
 */
public final class KeycloakTokens {

  /**
   * Client público do realm para os fluxos de usuário (ver graphrag-realm.json).
   */
  public static final String CLIENT_API = "graphrag-api";
  /**
   * Client de vida curtíssima (1s) — existe só para produzir tokens expirados nos testes.
   */
  public static final String CLIENT_E2E_SHORT_LIVED = "graphrag-e2e";

  private static final Map<String, CachedToken> CACHE = new ConcurrentHashMap<>();
  private static final RestClient HTTP = RestClient.create();

  private KeycloakTokens() {
  }

  /**
   * Token válido do usuário seedado (senha = username), cacheado até perto de expirar.
   */
  public static String userToken(final String issuerUri, final String username) {
    final var cached = CACHE.compute(username, (user, current) ->
      current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(10))
        ? current
        : fetch(issuerUri, CLIENT_API, user));
    return cached.value();
  }

  /**
   * Token realmente expirado: emitido pelo client de lifespan 1s e aguardado até vencer.
   */
  public static String expiredToken(final String issuerUri, final String username) {
    final var token = fetch(issuerUri, CLIENT_E2E_SHORT_LIVED, username);
    try {
      Thread.sleep(Math.max(0, Instant.now().until(token.expiresAt(), java.time.temporal.ChronoUnit.MILLIS)) + 1500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrompido aguardando expiração do token", e);
    }
    return token.value();
  }

  private static CachedToken fetch(final String issuerUri, final String clientId, final String username) {
    final var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", clientId);
    form.add("username", username);
    form.add("password", username); // convenção do realm de DEV: senha = username

    final var body = HTTP.post()
      .uri(issuerUri + "/protocol/openid-connect/token")
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .retrieve()
      .body(TokenResponse.class);

    if (body == null || body.accessToken() == null) {
      throw new IllegalStateException("Keycloak não devolveu access_token para " + username);
    }
    return new CachedToken(body.accessToken(), Instant.now().plusSeconds(body.expiresIn()));
  }

  private record CachedToken(String value, Instant expiresAt) {
  }

  private record TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") long expiresIn
  ) {
  }
}
