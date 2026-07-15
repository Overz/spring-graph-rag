package com.github.overz.api.internal.mappers;

import com.github.overz.api.internal.dtos.UploadAcceptedResponse;
import com.github.overz.rag.RegisteredDocument;

/**
 * Tradução do resultado público do {@code rag} para o contrato HTTP do módulo {@code api}
 * — o DTO de resposta nunca vaza para dentro do {@code rag}, nem o record do {@code rag}
 * vira contrato HTTP por acidente.
 */
public final class UploadResponseMapper {

  private UploadResponseMapper() {
  }

  public static UploadAcceptedResponse toResponse(final RegisteredDocument registered) {
    return UploadAcceptedResponse.builder()
      .id(registered.id())
      .status(registered.status().name())
      .correlationId(registered.correlationId())
      .version(registered.version())
      .build();
  }

}
