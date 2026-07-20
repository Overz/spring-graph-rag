package com.github.overz.api.internal.auth;

import com.github.overz.shared.support.OpaqueTokenGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OpaqueTokenGeneratorTest extends Assertions {

  private final OpaqueTokenGenerator generator = new OpaqueTokenGenerator();

  @Test
  void geraTokensDiferentesACadaChamada() {
    assertNotEquals(generator.generate(), generator.generate());
  }

  @Test
  void naoGeraTokenComFormatoDeJwt() {
    assertEquals(0, generator.generate().chars().filter(c -> c == '.').count());
  }

}
