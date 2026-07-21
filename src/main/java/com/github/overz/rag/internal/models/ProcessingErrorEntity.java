package com.github.overz.rag.internal.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Linha de {@code processing_errors} (RF28, schema já existe desde o Épico 0). Épico 2
 * só grava aqui a falha de um store (Neo4j/OpenSearch) durante a exclusão lógica síncrona
 * (design.md D2) — não há retry próprio, fica pra reconciliação do Épico 8 (RF38).
 */
@Entity
@Table(name = "processing_errors")
@Getter
@Setter
@NoArgsConstructor
public class ProcessingErrorEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private String stage;

  @Column(name = "error_code", nullable = false)
  private String errorCode;

  @Column
  private String message;

  @Column(nullable = false)
  private int attempt;

  @Column(name = "transient", nullable = false)
  private boolean transientFailure;

  @Column(name = "correlation_id", nullable = false)
  private String correlationId;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  public static ProcessingErrorEntity of(
    final UUID documentId,
    final String stage,
    final String errorCode,
    final String message,
    final String correlationId,
    final OffsetDateTime occurredAt
  ) {
    final var entity = new ProcessingErrorEntity();
    entity.setDocumentId(documentId);
    entity.setStage(stage);
    entity.setErrorCode(errorCode);
    entity.setMessage(message);
    entity.setAttempt(1);
    entity.setTransientFailure(true);
    entity.setCorrelationId(correlationId);
    entity.setOccurredAt(occurredAt);
    return entity;
  }

}
