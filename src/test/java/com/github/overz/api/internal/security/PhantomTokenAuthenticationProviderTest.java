package com.github.overz.api.internal.security;

import com.github.overz.api.internal.auth.PhantomToken;
import com.github.overz.api.internal.auth.PhantomTokenIssuer;
import com.github.overz.shared.security.CallerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.util.Optional;
import java.util.Set;

class PhantomTokenAuthenticationProviderTest extends Assertions {

  @Test
  void autenticaQuandoTokenOpacoResolveParaUmCallerContext() {
    final var provider = new PhantomTokenAuthenticationProvider(
      phantomTokenIssuer(Optional.of(new CallerContext("tenant-1", "owner-1", Set.of("document:upload")))));

    final var result = provider.authenticate(new BearerTokenAuthenticationToken("token-valido"));

    assertTrue(result.getPrincipal() instanceof CallerContext);
  }

  @Test
  void rejeitaQuandoTokenOpacoNaoResolve() {
    final var provider = new PhantomTokenAuthenticationProvider(phantomTokenIssuer(Optional.empty()));

    assertThrows(InvalidBearerTokenException.class, () ->
      provider.authenticate(new BearerTokenAuthenticationToken("token-invalido")));
  }

  private PhantomTokenIssuer phantomTokenIssuer(final Optional<CallerContext> resolved) {
    return new PhantomTokenIssuer() {
      @Override
      public PhantomToken issue(final String username, final String password) {
        throw new UnsupportedOperationException();
      }

      @Override
      public PhantomToken refresh(final String phantomTokenValue) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void revoke(final String phantomTokenValue) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<CallerContext> resolve(final String phantomTokenValue) {
        return resolved;
      }
    };
  }

}
