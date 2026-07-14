package com.github.overz.api.internal.security;

import com.github.overz.shared.CallerContext;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication cujo principal é o {@link CallerContext} — é o que o argument resolver
 * entrega aos controllers, mantendo o JWT cru fora do código de domínio.
 */
final class CallerContextAuthenticationToken extends AbstractAuthenticationToken {

  private final transient Jwt token;
  private final transient CallerContext callerContext;

  CallerContextAuthenticationToken(
    final Jwt token,
    final CallerContext callerContext,
    final Collection<? extends GrantedAuthority> authorities
  ) {
    super(authorities);
    this.token = token;
    this.callerContext = callerContext;
    setAuthenticated(true);
  }

  @Override
  public CallerContext getPrincipal() {
    return callerContext;
  }

  @Override
  public Jwt getCredentials() {
    return token;
  }

}
