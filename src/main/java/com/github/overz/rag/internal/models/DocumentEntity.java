package com.github.overz.rag.internal.models;

import com.github.overz.rag.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Linha de {@code documents} — schema V1 do Épico 0 ({@code docs/sdd/dados.md} §2).
 * Entidade interna ao módulo: fora do {@code rag} só circulam os records públicos.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
public class DocumentEntity {

  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Column(name = "owner_id", nullable = false)
  private String ownerId;

  @Column(nullable = false)
  private String filename;

  @Column(nullable = false)
  private String extension;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "file_hash_sha256", nullable = false)
  private String fileHashSha256;

  @Column(name = "raw_storage_key", nullable = false)
  private String rawStorageKey;

  @Column(name = "extracted_storage_key")
  private String extractedStorageKey;

  @Column(name = "transformed_storage_key")
  private String transformedStorageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DocumentStatus status;

  @Column(name = "embedding_status")
  private String embeddingStatus;

  @Column(name = "graph_status")
  private String graphStatus;

  @Column(nullable = false)
  private int version;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "lgpd_redacted", nullable = false)
  private boolean lgpdRedacted;

  @Column(name = "correlation_id", nullable = false)
  private String correlationId;

  @Column(name = "uploaded_at", nullable = false)
  private OffsetDateTime uploadedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

}
