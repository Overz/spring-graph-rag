package com.github.overz.api.internal.security;

import com.github.overz.shared.logging.ILogger;
import com.github.overz.shared.logging.LoggerFactory;
import com.github.overz.shared.security.CallerContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Conversor único de claims → {@link CallerContext} (ADL-008): token sintaticamente válido
 * mas sem a claim {@code tenantId} é rejeitado com 401 — identidade sem tenant não existe
 * neste sistema. Roles de realm (claim {@code realm_access.roles}) viram authorities e
 * ficam disponíveis no contexto para as decisões de autorização das camadas seguintes.
 */
public final class CallerContextJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final ILogger log = LoggerFactory.of(CallerContextJwtConverter.class);

  private static final String CLAIM_TENANT_ID = "tenantId";
  private static final String CLAIM_REALM_ACCESS = "realm_access";
  private static final String CLAIM_ROLES = "roles";

  @Override
  public AbstractAuthenticationToken convert(final Jwt jwt) {
    final var context = toCallerContext(jwt);
    final var authorities = context.roles().stream()
      .map(SimpleGrantedAuthority::new)
      .collect(Collectors.toUnmodifiableSet());
    return new CallerContextAuthenticationToken(jwt, context, authorities);
  }

  /**
   * Extração de claims reaproveitada pelo login por phantom token (ADR-004): o access
   * token recém-emitido pelo Keycloak passa pelas mesmas regras (tenantId obrigatória,
   * roles de realm) antes de virar {@link CallerContext} cacheado no Redis.
   */
  public static CallerContext toCallerContext(final Jwt jwt) {
    final var tenantId = jwt.getClaimAsString(CLAIM_TENANT_ID);
    if (tenantId == null || tenantId.isBlank()) {
      log.warn("Token rejeitado: claim '{}' ausente para sub='{}' — identidade sem tenant não existe (ADL-008)",
        CLAIM_TENANT_ID, jwt.getSubject());
      throw new InvalidBearerTokenException("Token sem a claim obrigatória 'tenantId'");
    }
    return new CallerContext(tenantId, jwt.getSubject(), realmRoles(jwt));
  }

  private static Set<String> realmRoles(final Jwt jwt) {
    final Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
    if (realmAccess == null || !(realmAccess.get(CLAIM_ROLES) instanceof List<?> raw)) {
      return Set.of();
    }
    return raw.stream()
      .map(String::valueOf)
      .collect(Collectors.toUnmodifiableSet());
  }

}
