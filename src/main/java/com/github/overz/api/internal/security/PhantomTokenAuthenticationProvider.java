package com.github.overz.api.internal.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

import java.util.stream.Collectors;

/**
 * Resolve o caminho de token opaco (ADR-004 D2): consulta {@link PhantomTokenIssuer} em
 * vez de decodificar/validar um JWT — usado pelo {@code AuthenticationManagerResolver}
 * do {@code SecurityConfig} quando o bearer token recebido não tem formato de JWT.
 */
@RequiredArgsConstructor
public final class PhantomTokenAuthenticationProvider implements AuthenticationProvider {

  private final PhantomTokenIssuer phantomTokenIssuer;

  @Override
  public Authentication authenticate(final Authentication authentication) {
    final var bearer = (BearerTokenAuthenticationToken) authentication;
    final var context = phantomTokenIssuer.resolve(bearer.getToken())
      .orElseThrow(() -> new InvalidBearerTokenException("Token opaco inválido, expirado ou revogado"));
    final var authorities = context.roles().stream()
      .map(SimpleGrantedAuthority::new)
      .collect(Collectors.toUnmodifiableSet());
    return new PhantomTokenAuthenticationToken(bearer.getToken(), context, authorities);
  }

  @Override
  public boolean supports(final Class<?> authentication) {
    return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
  }

}
