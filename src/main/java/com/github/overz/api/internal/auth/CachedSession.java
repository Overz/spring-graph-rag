package com.github.overz.api.internal.auth;

import java.util.Set;

/** Estado cacheado no Redis por trás de um token opaco (ADR-004 D3). */
record CachedSession(String tenantId, String ownerId, Set<String> roles, String keycloakRefreshToken) {
}
