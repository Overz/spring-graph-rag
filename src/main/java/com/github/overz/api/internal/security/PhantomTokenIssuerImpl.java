package com.github.overz.api.internal.security;

import com.github.overz.api.internal.configs.AuthProperties;
import com.github.overz.api.internal.dtos.CachedSession;
import com.github.overz.api.internal.dtos.IdentityProviderGrantResponse;
import com.github.overz.api.internal.dtos.PhantomToken;
import com.github.overz.api.internal.errors.InvalidCredentialsException;
import com.github.overz.api.internal.errors.InvalidPhantomTokenException;
import com.github.overz.api.internal.repositories.PhantomTokenRepository;
import com.github.overz.shared.security.CallerContext;
import com.github.overz.shared.support.OpaqueTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Orquestra o fluxo de login por phantom token (RF35, ADR-004): password/refresh_token
 * grant contra o Keycloak (realm e client fixos — {@link #exchange}, único ponto de
 * comunicação com o IdP), extração de {@link CallerContext} do access token real (mesma
 * regra do resource server — {@link CallerContextJwtConverter#toCallerContext}), e o
 * mapeamento pro token opaco guardado no Redis.
 */
@RequiredArgsConstructor
public final class PhantomTokenIssuerImpl implements PhantomTokenIssuer {

  private final RestClient http;
  private final AuthProperties properties;
  private final PhantomTokenRepository repository;
  private final JwtDecoder jwtDecoder;

  @Override
  public PhantomToken issue(final String username, final String password) {
    final var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "password");
    form.add("client_id", properties.identityProviderClientId());
    form.add("username", username);
    form.add("password", password);
    try {
      return cache(OpaqueTokenGenerator.generate(), exchange(form));
    } catch (RestClientResponseException e) {
      throw new InvalidCredentialsException();
    }
  }

  @Override
  public PhantomToken refresh(final String phantomTokenValue) {
    final var cached = repository.find(phantomTokenValue).orElseThrow(InvalidPhantomTokenException::new);
    final var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "refresh_token");
    form.add("client_id", properties.identityProviderClientId());
    form.add("refresh_token", cached.keycloakRefreshToken());
    try {
      return cache(phantomTokenValue, exchange(form));
    } catch (RestClientResponseException e) {
      throw new InvalidPhantomTokenException();
    }
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

  /** Único ponto de comunicação HTTP com o Keycloak — password e refresh_token grant só variam a forma. */
  private IdentityProviderGrantResponse exchange(final LinkedMultiValueMap<String, String> form) {
    return http.post()
      .uri(properties.identityProviderAuthUri())
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .retrieve()
      .body(IdentityProviderGrantResponse.class);
  }

  private PhantomToken cache(final String opaqueValue, final IdentityProviderGrantResponse grant) {
    final var jwt = jwtDecoder.decode(grant.accessToken());
    final var context = CallerContextJwtConverter.toCallerContext(jwt);
    repository.save(
      opaqueValue,
      new CachedSession(context.tenantId(), context.ownerId(), context.roles(), grant.refreshToken()),
      grant.refreshExpiresIn()
    );
    return new PhantomToken(opaqueValue, grant.refreshExpiresIn());
  }

}
