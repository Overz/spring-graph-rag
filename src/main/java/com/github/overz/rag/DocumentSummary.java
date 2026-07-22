package com.github.overz.rag;

import lombok.Builder;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Um item de {@code GET /api/v1/documents} (RF40): visão compartilhada entre todos os
 * usuários do tenant — {@code ownerId} distingue quem enviou cada documento.
 *
 * <p>{@code status} é {@link DocumentStatus#DELETED} pra documento excluído logicamente
 * — persistido assim por {@code softDelete}, refletido direto da coluna, sem lógica
 * condicional aqui. {@code active} expõe o mesmo fato como boolean, mais direto pra
 * quem só quer checar liveness sem comparar string de status.
 */
@Builder
public record DocumentSummary(
  UUID id,
  String filename,
  DocumentStatus status,
  boolean active,
  String ownerId,
  int version,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) implements Serializable {
}
