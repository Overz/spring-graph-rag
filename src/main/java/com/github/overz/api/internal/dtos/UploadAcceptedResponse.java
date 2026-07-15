package com.github.overz.api.internal.dtos;

import lombok.Builder;

import java.io.Serializable;
import java.util.UUID;

/**
 * Corpo do {@code 202 Accepted} do upload (RF01, SDD ingestao §1). Só serializado para
 * JSON, nunca desserializado — {@code @Jacksonized} não se aplica aqui (é para builders
 * usados na leitura de JSON de entrada, que este DTO não tem).
 */
@Builder
public record UploadAcceptedResponse(
  UUID id,
  String status,
  String correlationId,
  int version
) implements Serializable {
}
