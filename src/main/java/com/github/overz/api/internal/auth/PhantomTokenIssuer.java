package com.github.overz.api.internal.auth;

import com.github.overz.shared.security.CallerContext;

import java.util.Optional;

/**
 * Contrato do fluxo de login por phantom token (RF35, change auth-phantom-token, ADR-004
 * D5) — controller e resource server dependem só desta interface; trocar a implementação
 * (outra estratégia de token store, ou uma v2 do fluxo) não toca em nenhum dos dois.
 */
public interface PhantomTokenIssuer {

  /** @throws com.github.overz.api.internal.errors.InvalidCredentialsException usuário/senha incorretos */
  PhantomToken issue(String username, String password);

  /** @throws com.github.overz.api.internal.errors.InvalidPhantomTokenException token inexistente/expirado/revogado */
  PhantomToken refresh(String phantomTokenValue);

  /** Idempotente: revogar um token já revogado/inexistente não é erro. */
  void revoke(String phantomTokenValue);

  /** Usado pelo resource server (SecurityConfig) para resolver um token opaco recebido. */
  Optional<CallerContext> resolve(String phantomTokenValue);

}
