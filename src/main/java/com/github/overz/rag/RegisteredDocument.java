package com.github.overz.rag;

import lombok.Builder;

import java.io.Serializable;
import java.util.UUID;

/**
 * Resultado do registro de um upload aceito — o que o {@code 202} do upload devolve
 * ao cliente (SDD ingestao §1).
 */
@Builder
public record RegisteredDocument(
  UUID id,
  DocumentStatus status,
  String correlationId,
  int version
) implements Serializable {
}
