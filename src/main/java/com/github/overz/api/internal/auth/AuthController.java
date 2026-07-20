package com.github.overz.api.internal.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF35 (ADR-004) — {@code login}/{@code refresh}/{@code logout} de usuário via phantom
 * token. Realm e {@code grant_type} fixos por endpoint; identidade só via {@link PhantomTokenIssuer}.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

  private final PhantomTokenIssuer phantomTokenIssuer;

  @PostMapping("/api/v1/auth/login")
  PhantomTokenResponse login(@Valid @RequestBody final LoginRequest request) {
    final var token = phantomTokenIssuer.issue(request.username(), request.password());
    return AuthResponseMapper.toResponse(token);
  }

  @PostMapping("/api/v1/auth/refresh")
  PhantomTokenResponse refresh(@RequestHeader(HttpHeaders.AUTHORIZATION) final String authorization) {
    final var token = phantomTokenIssuer.refresh(bearerValue(authorization));
    return AuthResponseMapper.toResponse(token);
  }

  @PostMapping("/api/v1/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) final String authorization) {
    phantomTokenIssuer.revoke(bearerValue(authorization));
  }

  private static String bearerValue(final String authorization) {
    final var prefix = "Bearer ";
    return authorization.startsWith(prefix) ? authorization.substring(prefix.length()) : authorization;
  }

}
