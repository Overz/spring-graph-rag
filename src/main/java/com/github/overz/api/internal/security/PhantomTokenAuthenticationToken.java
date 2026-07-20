package com.github.overz.api.internal.security;

import com.github.overz.shared.security.CallerContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Authentication cujo principal é o {@link CallerContext}, análoga a
 * {@link CallerContextAuthenticationToken} mas para o caminho de token opaco (ADR-004 D2)
 * — {@link CallerContextArgumentResolver} funciona idêntico nos dois caminhos.
 */
final class PhantomTokenAuthenticationToken extends AbstractAuthenticationToken {

  private final transient String token;
  private final transient CallerContext callerContext;

  PhantomTokenAuthenticationToken(
    final String token,
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
  public String getCredentials() {
    return token;
  }

}
