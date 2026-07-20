package com.github.overz.api.internal.auth;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Password grant e refresh_token grant fixos contra o Keycloak (realm {@code graphrag},
 * client fixo) — mesmo endpoint/formato que {@code KeycloakTokens} já usa em teste, agora
 * em código de produção por trás do login (ADR-004).
 */
public final class KeycloakTokenClient {

  private static final String TOKEN_PATH = "/protocol/openid-connect/token";

  private final RestClient http = RestClient.create();
  private final String issuer;
  private final String clientId;

  public KeycloakTokenClient(final String issuer, final String clientId) {
    this.issuer = issuer;
    this.clientId = clientId;
  }

  KeycloakGrantResponse passwordGrant(final String username, final String password) {
    final var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", clientId);
    form.add("username", username);
    form.add("password", password);
    return exchange(form);
  }

  KeycloakGrantResponse refreshGrant(final String keycloakRefreshToken) {
    final var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "refresh_token");
    form.add("client_id", clientId);
    form.add("refresh_token", keycloakRefreshToken);
    return exchange(form);
  }

  private KeycloakGrantResponse exchange(final LinkedMultiValueMap<String, String> form) {
    return http.post()
      .uri(issuer + TOKEN_PATH)
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .retrieve()
      .body(KeycloakGrantResponse.class);
  }

}
