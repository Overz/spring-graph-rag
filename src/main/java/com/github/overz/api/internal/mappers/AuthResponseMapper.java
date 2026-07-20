package com.github.overz.api.internal.mappers;

import com.github.overz.api.internal.dtos.PhantomToken;
import com.github.overz.api.internal.dtos.PhantomTokenResponse;

public final class AuthResponseMapper {

  private AuthResponseMapper() {
  }

  public static PhantomTokenResponse toResponse(final PhantomToken token) {
    return PhantomTokenResponse.builder()
      .token(token.value())
      .expiresInSeconds(token.expiresInSeconds())
      .build();
  }

}
