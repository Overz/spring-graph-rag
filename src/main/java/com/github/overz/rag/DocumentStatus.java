package com.github.overz.rag;

/**
 * Ciclo de vida do documento (RF08) + status de falha por etapa (RF27). Único vocabulário
 * de estados do sistema — persistido como texto na coluna {@code documents.status}.
 * Toda transição é responsabilidade do lifecycle do módulo {@code rag} (SDD ingestao §6).
 */
public enum DocumentStatus {

  RECEIVED,
  VALIDATING,
  UPLOADED,
  QUEUED,
  EXTRACTING,
  TRANSFORMING,
  CHUNKING,
  EMBEDDING,
  GRAPH_BUILDING,
  COMPLETED,
  PARTIALLY_COMPLETED,
  FAILED,

  // Falhas por etapa (RF27): o documento fica nelas aguardando retry; retries esgotados → FAILED.
  EXTRACTION_FAILED,
  TRANSFORMATION_FAILED,
  CHUNKING_FAILED,
  EMBEDDING_FAILED,
  GRAPH_BUILDING_FAILED,

  // Estado terminal da exclusão lógica (RF10/RF31) — não é etapa de pipeline, mas é
  // persistido em documents.status por softDelete (RF40: listagem/leitura direta
  // precisam ver a exclusão sem lógica condicional própria); isActive continua sendo o
  // flag estrutural que os stores/filtros usam, DELETED é só a representação legível do
  // mesmo evento. O último status real de pipeline fica preservado em
  // document_status_history.from_status na linha da exclusão.
  DELETED

}
