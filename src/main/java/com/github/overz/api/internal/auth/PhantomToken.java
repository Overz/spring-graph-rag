package com.github.overz.api.internal.auth;

import lombok.Builder;

import java.io.Serializable;

/** Token opaco emitido pelo login (RF35, ADR-004) — valor nunca decodificável pelo cliente. */
@Builder
public record PhantomToken(String value, long expiresInSeconds) implements Serializable {
}
