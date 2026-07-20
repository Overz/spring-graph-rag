package com.github.overz.api.internal.auth;

import java.security.SecureRandom;
import java.util.Base64;

/** Token opaco = identificador aleatório de alta entropia (ADR-004 D4) — nunca um JWT. */
public final class OpaqueTokenGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  String generate() {
    final var bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

}
