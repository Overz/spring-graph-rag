package com.github.overz.api.internal.auth;

final class AuthResponseMapper {

  private AuthResponseMapper() {
  }

  static PhantomTokenResponse toResponse(final PhantomToken token) {
    return PhantomTokenResponse.builder()
      .token(token.value())
      .expiresInSeconds(token.expiresInSeconds())
      .build();
  }

}
