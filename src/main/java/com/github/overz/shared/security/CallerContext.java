package com.github.overz.shared.security;

import lombok.Builder;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
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
@Builder
public record CallerContext(String tenantId, String ownerId, Set<String> roles) implements Serializable {

  public CallerContext {
    if (StringUtils.isBlank(tenantId)) {
      throw new IllegalArgumentException("tenantId não pode ser vazio");
    }
    if (StringUtils.isBlank(ownerId)) {
      throw new IllegalArgumentException("ownerId não pode ser vazio");
    }
    roles = Set.copyOf(roles);
  }

  public boolean hasRole(final String role) {
    return roles.contains(role);
  }

}
