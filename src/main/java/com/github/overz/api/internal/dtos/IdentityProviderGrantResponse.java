package com.github.overz.api.internal.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IdentityProviderGrantResponse(
  @JsonProperty("access_token") String accessToken,
  @JsonProperty("refresh_token") String refreshToken,
  @JsonProperty("expires_in") long expiresIn,
  @JsonProperty("refresh_expires_in") long refreshExpiresIn
) {
}
