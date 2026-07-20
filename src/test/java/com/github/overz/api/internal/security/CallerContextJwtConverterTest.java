package com.github.overz.api.internal.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;
import java.util.Map;

class CallerContextJwtConverterTest extends Assertions {

  @Test
  void rejeitaTokenSemClaimTenantId() {
    final var jwt = Jwt.withTokenValue("token")
      .header("alg", "none")
      .claim("sub", "user-1")
      .build();

    assertThrows(InvalidBearerTokenException.class, () -> CallerContextJwtConverter.toCallerContext(jwt));
  }

  @Test
  void extraiRolesDoRealmAccess() {
    final var jwt = Jwt.withTokenValue("token")
      .header("alg", "none")
      .claim("sub", "user-1")
      .claim("tenantId", "tenant-1")
      .claim("realm_access", Map.of("roles", List.of("document:upload")))
      .build();

    assertTrue(CallerContextJwtConverter.toCallerContext(jwt).hasRole("document:upload"));
  }

}
