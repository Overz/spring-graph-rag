package com.github.overz.api.internal.dtos;

import lombok.Builder;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Um item de {@code GET /api/v1/documents} (RF40). */
@Builder
public record DocumentSummaryResponse(
  UUID id,
  String filename,
  String status,
  String ownerId,
  int version,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) implements Serializable {
}
