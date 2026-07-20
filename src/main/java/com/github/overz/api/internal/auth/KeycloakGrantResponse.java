package com.github.overz.api.internal.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

record KeycloakGrantResponse(
  @JsonProperty("access_token") String accessToken,
  @JsonProperty("refresh_token") String refreshToken,
  @JsonProperty("expires_in") long expiresIn,
  @JsonProperty("refresh_expires_in") long refreshExpiresIn
) {
}
