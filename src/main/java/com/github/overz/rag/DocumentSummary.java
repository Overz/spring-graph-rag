package com.github.overz.rag;

import lombok.Builder;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um item de {@code GET /api/v1/documents} (RF40): visão compartilhada entre todos os
 * usuários do tenant — {@code ownerId} distingue quem enviou cada documento.
 */
@Builder
public record DocumentSummary(
  UUID id,
  String filename,
  DocumentStatus status,
  String ownerId,
  int version,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) implements Serializable {
}
