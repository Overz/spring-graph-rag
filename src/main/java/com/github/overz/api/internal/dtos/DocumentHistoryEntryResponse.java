package com.github.overz.api.internal.dtos;

import lombok.Builder;

import java.io.Serializable;
import java.time.OffsetDateTime;

/** Um item de {@code GET /api/v1/documents/{id}/history} (RF09). */
@Builder
public record DocumentHistoryEntryResponse(
  String status,
  OffsetDateTime occurredAt,
  String detail
) implements Serializable {
}
