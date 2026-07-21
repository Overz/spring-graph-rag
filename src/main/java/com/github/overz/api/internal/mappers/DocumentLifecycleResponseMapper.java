package com.github.overz.api.internal.mappers;

import com.github.overz.api.internal.dtos.DocumentHistoryEntryResponse;
import com.github.overz.api.internal.dtos.DocumentStatusResponse;
import com.github.overz.rag.DocumentHistoryEntry;
import com.github.overz.rag.DocumentStatus;

import java.util.List;

/** Tradução dos records públicos do {@code rag} pro contrato HTTP de status/histórico. */
public final class DocumentLifecycleResponseMapper {

  private DocumentLifecycleResponseMapper() {
  }

  public static DocumentStatusResponse toResponse(final DocumentStatus status) {
    return DocumentStatusResponse.builder().status(status.name()).build();
  }

  public static List<DocumentHistoryEntryResponse> toResponse(final List<DocumentHistoryEntry> entries) {
    return entries.stream()
      .map(entry -> DocumentHistoryEntryResponse.builder()
        .status(entry.status().name())
        .occurredAt(entry.occurredAt())
        .detail(entry.detail())
        .build())
      .toList();
  }

}
