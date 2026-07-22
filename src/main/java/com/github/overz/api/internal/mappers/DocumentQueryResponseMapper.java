package com.github.overz.api.internal.mappers;

import com.github.overz.api.internal.dtos.DocumentSummaryResponse;
import com.github.overz.api.internal.dtos.PagedResponse;
import com.github.overz.rag.DocumentSummary;
import org.springframework.data.domain.Page;

/** Tradução da página pública do {@code rag} pro contrato HTTP de {@code GET /api/v1/documents}. */
public final class DocumentQueryResponseMapper {

  private DocumentQueryResponseMapper() {
  }

  public static PagedResponse<DocumentSummaryResponse> toResponse(final Page<DocumentSummary> page) {
    return PagedResponse.<DocumentSummaryResponse>builder()
      .content(page.getContent().stream().map(DocumentQueryResponseMapper::toResponse).toList())
      .page(page.getNumber())
      .size(page.getSize())
      .totalElements(page.getTotalElements())
      .totalPages(page.getTotalPages())
      .build();
  }

  private static DocumentSummaryResponse toResponse(final DocumentSummary summary) {
    return DocumentSummaryResponse.builder()
      .id(summary.id())
      .filename(summary.filename())
      .status(summary.status().name())
      .active(summary.active())
      .ownerId(summary.ownerId())
      .version(summary.version())
      .createdAt(summary.createdAt())
      .updatedAt(summary.updatedAt())
      .build();
  }

}
