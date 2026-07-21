package com.github.overz.rag;

import lombok.Builder;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Uma transição do histórico de um documento (RF09): etapa alcançada, quando e por quê.
 */
@Builder
public record DocumentHistoryEntry(
  DocumentStatus status,
  OffsetDateTime occurredAt,
  String detail
) implements Serializable {
}
