package com.github.overz.api.internal.repositories;

import com.github.overz.api.internal.dtos.CachedSession;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Store Redis do phantom token (ADR-004 D3): chave {@code phantom-token:<token>}, campos
 * em hash — sem serialização JSON, evita qualquer dependência de Jackson clássico neste
 * módulo. TTL da chave é sempre a janela de refresh do Keycloak, não a do access token.
 */
@RequiredArgsConstructor
public final class PhantomTokenRedisRepository implements PhantomTokenRepository {

  private static final String KEY_PREFIX = "phantom-token:";
  private static final String FIELD_TENANT_ID = "tenantId";
  private static final String FIELD_OWNER_ID = "ownerId";
  private static final String FIELD_ROLES = "roles";
  private static final String FIELD_REFRESH_TOKEN = "keycloakRefreshToken";
  private static final String ROLES_DELIMITER = ",";

  private final StringRedisTemplate redis;

  @Override
  public void save(final String token, final CachedSession session, final long ttlSeconds) {
    final var key = KEY_PREFIX + token;
    final var ops = redis.<String, String>opsForHash();
    ops.put(key, FIELD_TENANT_ID, session.tenantId());
    ops.put(key, FIELD_OWNER_ID, session.ownerId());
    ops.put(key, FIELD_ROLES, String.join(ROLES_DELIMITER, session.roles()));
    ops.put(key, FIELD_REFRESH_TOKEN, session.keycloakRefreshToken());
    redis.expire(key, Duration.ofSeconds(ttlSeconds));
  }

  @Override
  public Optional<CachedSession> find(final String token) {
    final var entries = redis.<String, String>opsForHash().entries(KEY_PREFIX + token);
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    final var rolesField = entries.get(FIELD_ROLES);
    final Set<String> roles = StringUtils.isBlank(rolesField)
      ? Set.of()
      : Set.of(rolesField.split(ROLES_DELIMITER));
    return Optional.of(new CachedSession(
      entries.get(FIELD_TENANT_ID),
      entries.get(FIELD_OWNER_ID),
      roles,
      entries.get(FIELD_REFRESH_TOKEN)
    ));
  }

  @Override
  public void delete(final String token) {
    redis.delete(KEY_PREFIX + token);
  }

}
