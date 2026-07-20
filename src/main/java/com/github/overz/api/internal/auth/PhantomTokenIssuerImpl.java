package com.github.overz.api.internal.auth;

import com.github.overz.api.internal.errors.InvalidCredentialsException;
import com.github.overz.api.internal.errors.InvalidPhantomTokenException;
import com.github.overz.api.internal.security.CallerContextJwtConverter;
import com.github.overz.shared.security.CallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Orquestra o fluxo de login por phantom token (RF35, ADR-004): password/refresh_token
 * grant contra o Keycloak, extração de {@link CallerContext} do access token real (mesma
 * regra do resource server — {@link CallerContextJwtConverter#toCallerContext}), e o
 * mapeamento pro token opaco guardado no Redis.
 */
@RequiredArgsConstructor
public final class PhantomTokenIssuerImpl implements PhantomTokenIssuer {

  private final KeycloakTokenClient keycloak;
  private final PhantomTokenRepository repository;
  private final OpaqueTokenGenerator tokenGenerator;
  private final JwtDecoder jwtDecoder;

  @Override
  public PhantomToken issue(final String username, final String password) {
    final KeycloakGrantResponse grant;
    try {
      grant = keycloak.passwordGrant(username, password);
    } catch (RestClientResponseException e) {
      throw new InvalidCredentialsException();
    }
    return cache(tokenGenerator.generate(), grant);
  }

  @Override
  public PhantomToken refresh(final String phantomTokenValue) {
    final var cached = repository.find(phantomTokenValue).orElseThrow(InvalidPhantomTokenException::new);
    final KeycloakGrantResponse grant;
    try {
      grant = keycloak.refreshGrant(cached.keycloakRefreshToken());
    } catch (RestClientResponseException e) {
      throw new InvalidPhantomTokenException();
    }
    return cache(phantomTokenValue, grant);
  }

  @Override
  public void revoke(final String phantomTokenValue) {
    repository.delete(phantomTokenValue);
  }

  @Override
  public Optional<CallerContext> resolve(final String phantomTokenValue) {
    return repository.find(phantomTokenValue)
      .map(cached -> new CallerContext(cached.tenantId(), cached.ownerId(), cached.roles()));
  }

  private PhantomToken cache(final String opaqueValue, final KeycloakGrantResponse grant) {
    final var jwt = jwtDecoder.decode(grant.accessToken());
    final var context = CallerContextJwtConverter.toCallerContext(jwt);
    repository.save(
      opaqueValue,
      new CachedSession(context.tenantId(), context.ownerId(), context.roles(), grant.refreshToken()),
      grant.refreshExpiresIn());
    return new PhantomToken(opaqueValue, grant.refreshExpiresIn());
  }

}
