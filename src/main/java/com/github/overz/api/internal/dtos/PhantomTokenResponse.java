package com.github.overz.api.internal.dtos;

import lombok.Builder;

import java.io.Serializable;

/** Corpo de resposta do login/refresh — só serializado, nunca desserializado. */
@Builder
public record PhantomTokenResponse(String token, long expiresInSeconds) implements Serializable {
}
