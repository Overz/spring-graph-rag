package com.github.overz.rag.internal.models;

import com.github.overz.rag.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Linha de {@code document_status_history} (RF09): uma por transição; {@code branch}
 * distingue transições de sub-estado do fork-join (Épicos 5–6).
 */
@Entity
@Table(name = "document_status_history")
@Getter
@Setter
@NoArgsConstructor
public class DocumentStatusHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status")
  private DocumentStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false)
  private DocumentStatus toStatus;

  @Column(name = "branch")
  private String branch;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "detail")
  private String detail;

  public static DocumentStatusHistoryEntity transition(
    final UUID documentId,
    final DocumentStatus from,
    final DocumentStatus to,
    final OffsetDateTime occurredAt,
    final String detail
  ) {
    final var entity = new DocumentStatusHistoryEntity();
    entity.setDocumentId(documentId);
    entity.setFromStatus(from);
    entity.setToStatus(to);
    entity.setOccurredAt(occurredAt);
    entity.setDetail(detail);
    return entity;
  }

}
