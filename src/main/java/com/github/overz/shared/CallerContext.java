package com.github.overz.shared;

import java.util.Set;

/**
 * Identidade que atravessa os módulos (RF30/ADL-008): {@code tenantId} e {@code ownerId}
 * saem <strong>sempre</strong> das claims do JWT — nunca do corpo da requisição.
 * Controllers e tools MCP enxergam apenas este contrato; nenhum código de domínio lê token cru.
 *
 * @param tenantId claim {@code tenantId} do realm — identidade sem tenant não existe
 * @param ownerId  claim {@code sub} (identidade estável do usuário no realm)
 * @param roles    roles de realm granulares por operação (ex.: {@code document:upload})
 */
public record CallerContext(String tenantId, String ownerId, Set<String> roles) {

  public CallerContext {
    roles = Set.copyOf(roles);
  }

  public boolean hasRole(final String role) {
    return roles.contains(role);
  }

}
