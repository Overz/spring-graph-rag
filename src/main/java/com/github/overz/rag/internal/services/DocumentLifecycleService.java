package com.github.overz.rag.internal.services;

import com.github.overz.rag.AcceptedUpload;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.DocumentCommandOutcome;
import com.github.overz.rag.DocumentHistoryEntry;
import com.github.overz.rag.DocumentStatus;
import com.github.overz.rag.RegisteredDocument;
import com.github.overz.rag.TenantQuota;
import com.github.overz.rag.TenantUsage;
import com.github.overz.rag.VersionReplacementResult;
import com.github.overz.rag.internal.models.DocumentEntity;
import com.github.overz.rag.internal.models.DocumentStatusHistoryEntity;
import com.github.overz.rag.internal.models.ProcessingErrorEntity;
import com.github.overz.rag.internal.repositories.ChunkIndex;
import com.github.overz.rag.internal.repositories.DocumentGraphRepository;
import com.github.overz.rag.internal.repositories.DocumentRepository;
import com.github.overz.rag.internal.repositories.DocumentStatusHistoryRepository;
import com.github.overz.rag.internal.repositories.ProcessingErrorRepository;
import com.github.overz.rag.internal.repositories.TenantQuotaRepository;
import com.github.overz.shared.logging.ILogger;
import com.github.overz.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Único ponto de escrita de {@link DocumentStatus} (design.md 3.4): cobre o aceite do
 * upload (RF06/RF09), a consulta de status/histórico (RF09), a exclusão lógica com
 * isolamento de grafo (RF10) e a substituição de versão (RF10 complemento). Registrado
 * como bean em {@code RagConfig} — sem estereótipo de classe.
 */
@RequiredArgsConstructor
public class DocumentLifecycleService implements DocumentCommandApi {

  private static final ILogger log = LoggerFactory.of(DocumentLifecycleService.class);

  private final DocumentRepository documents;
  private final DocumentStatusHistoryRepository history;
  private final TenantQuotaRepository quotas;
  private final ProcessingErrorRepository processingErrors;
  private final DocumentGraphRepository documentGraph;
  private final ChunkIndex chunkIndex;

  @Override
  @Transactional
  public RegisteredDocument registerAcceptedUpload(final AcceptedUpload upload) {
    final var now = OffsetDateTime.now();
    // Inédito → 1; reenvio legítimo do mesmo conteúdo (anterior FAILED — a validação de
    // duplicidade já barrou os demais casos) → versão seguinte, preservando a linha falhada.
    final var version = documents.maxVersionOf(upload.tenantId(), upload.ownerId(), upload.sha256()) + 1;
    return persistUpload(upload, version, now);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean successfulDuplicateExists(final String tenantId, final String ownerId, final String sha256) {
    return documents.existsByTenantIdAndOwnerIdAndFileHashSha256AndStatusNotAndActiveTrue(
      tenantId, ownerId, sha256, DocumentStatus.FAILED);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TenantQuota> quotaOf(final String tenantId) {
    return quotas.findById(tenantId)
      .map(quota -> new TenantQuota(quota.getMaxStorageBytes(), quota.getMaxActiveFiles()));
  }

  @Override
  @Transactional(readOnly = true)
  public TenantUsage usageOf(final String tenantId) {
    return new TenantUsage(
      documents.sumActiveStorageBytes(tenantId),
      documents.countByTenantIdAndActiveTrue(tenantId));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DocumentStatus> statusOf(final UUID documentId, final String tenantId, final String ownerId) {
    return findVisibleTo(documentId, tenantId, ownerId).map(DocumentEntity::getStatus);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<List<DocumentHistoryEntry>> historyOf(
    final UUID documentId, final String tenantId, final String ownerId
  ) {
    return findVisibleTo(documentId, tenantId, ownerId)
      .map(document -> history.findByDocumentIdOrderByOccurredAtAsc(documentId).stream()
        .map(entry -> new DocumentHistoryEntry(entry.getToStatus(), entry.getOccurredAt(), entry.getDetail()))
        .toList());
  }

  @Override
  @Transactional
  public DocumentCommandOutcome deleteDocument(final UUID documentId, final String tenantId, final String ownerId) {
    final var found = findInTenant(documentId, tenantId);
    if (found.isEmpty()) {
      return DocumentCommandOutcome.NOT_FOUND;
    }
    final var document = found.get();
    if (!document.getOwnerId().equals(ownerId)) {
      return DocumentCommandOutcome.FORBIDDEN;
    }
    softDelete(document);
    return DocumentCommandOutcome.OK;
  }

  @Override
  @Transactional
  public VersionReplacementResult replaceVersion(final UUID previousDocumentId, final AcceptedUpload newUpload) {
    final var found = findInTenant(previousDocumentId, newUpload.tenantId());
    if (found.isEmpty()) {
      return VersionReplacementResult.notFound();
    }
    final var previous = found.get();
    if (!previous.getOwnerId().equals(newUpload.ownerId())) {
      return VersionReplacementResult.forbidden();
    }
    softDelete(previous);
    final var registered = persistUpload(newUpload, previous.getVersion() + 1, OffsetDateTime.now());
    return VersionReplacementResult.ok(registered);
  }

  private RegisteredDocument persistUpload(final AcceptedUpload upload, final int version, final OffsetDateTime now) {
    final var document = new DocumentEntity();
    document.setId(upload.id());
    document.setTenantId(upload.tenantId());
    document.setOwnerId(upload.ownerId());
    document.setFilename(upload.filename());
    document.setExtension(upload.extension());
    document.setContentType(upload.contentType());
    document.setFileSizeBytes(upload.fileSizeBytes());
    document.setFileHashSha256(upload.sha256());
    document.setRawStorageKey(upload.rawStorageKey());
    document.setStatus(DocumentStatus.UPLOADED);
    document.setVersion(version);
    document.setActive(true);
    document.setLgpdRedacted(false);
    document.setCorrelationId(upload.correlationId());
    document.setUploadedAt(now);
    documents.save(document);

    history.save(DocumentStatusHistoryEntity.transition(
      upload.id(), null, DocumentStatus.RECEIVED, now, "Upload recebido"));
    history.save(DocumentStatusHistoryEntity.transition(
      upload.id(), DocumentStatus.RECEIVED, DocumentStatus.VALIDATING, now, "Cadeia de validação iniciada"));
    history.save(DocumentStatusHistoryEntity.transition(
      upload.id(), DocumentStatus.VALIDATING, DocumentStatus.UPLOADED, now, "Validações aprovadas; original no storage"));

    log.info("Documento registrado: documentId='{}' tenant='{}' status='{}' correlationId='{}'",
      upload.id(), upload.tenantId(), DocumentStatus.UPLOADED, upload.correlationId());

    return RegisteredDocument.builder()
      .id(upload.id())
      .status(DocumentStatus.UPLOADED)
      .correlationId(upload.correlationId())
      .version(version)
      .build();
  }

  /**
   * Exclusão lógica síncrona (design.md D2): os 3 stores são atualizados na mesma
   * chamada; falha em Neo4j/OpenSearch vira linha em {@code processing_errors} em vez de
   * abortar a operação — o Postgres já está marcado {@code is_active=false} nesse ponto,
   * inconsistência temporária aceita (mitigação: reconciliação do Épico 8/RF38).
   */
  private void softDelete(final DocumentEntity document) {
    document.setActive(false);
    documents.save(document);

    // Rastro de auditoria da exclusão (RF31): DocumentStatus não tem estado "excluído" —
    // não é etapa de pipeline, é uma flag ortogonal (isActive) — então a transição não
    // muda de status (from == to, o que já tinha), só o detail explica o evento. Fica no
    // histórico para quando o audit_log dedicado (RF31, Épico futuro) existir; até lá,
    // não é mais consultável por GET /history assim que isActive vira false (ver
    // findVisibleTo) — a linha permanece no Postgres, só não é servida por este endpoint.
    history.save(DocumentStatusHistoryEntity.transition(
      document.getId(), document.getStatus(), document.getStatus(), OffsetDateTime.now(),
      "Documento excluído logicamente"));

    final var documentId = document.getId().toString();
    try {
      documentGraph.markInactive(documentId);
    } catch (final RuntimeException e) {
      logStoreFailure(document, "SOFT_DELETE_GRAPH", e);
    }
    try {
      chunkIndex.inactivateByDocumentId(documentId);
    } catch (final RuntimeException e) {
      logStoreFailure(document, "SOFT_DELETE_INDEX", e);
    }

    log.info("Documento inativado: documentId='{}' tenant='{}' correlationId='{}'",
      document.getId(), document.getTenantId(), document.getCorrelationId());
  }

  private void logStoreFailure(final DocumentEntity document, final String stage, final RuntimeException error) {
    log.error("Falha ao inativar store na exclusão lógica: documentId='{}' stage='{}' erro='{}'",
      document.getId(), stage, error.getMessage());
    processingErrors.save(ProcessingErrorEntity.of(
      document.getId(), stage, error.getClass().getSimpleName(), error.getMessage(),
      document.getCorrelationId(), OffsetDateTime.now()));
  }

  /**
   * {@code isActive} estrutural em todo read filter (CLAUDE.md, multitenancy): documento
   * excluído logicamente responde igual a inexistente — não é mais visível por aqui.
   */
  private Optional<DocumentEntity> findVisibleTo(final UUID documentId, final String tenantId, final String ownerId) {
    return documents.findById(documentId)
      .filter(DocumentEntity::isActive)
      .filter(document -> document.getTenantId().equals(tenantId))
      .filter(document -> document.getOwnerId().equals(ownerId));
  }

  /**
   * Mesma regra de {@link #findVisibleTo} pro comando de exclusão/versionamento: documento
   * já excluído não pode ser excluído/substituído de novo — responde {@code NOT_FOUND}
   * (não {@code FORBIDDEN}), consistente com "não existe mais" pra quem chama.
   */
  private Optional<DocumentEntity> findInTenant(final UUID documentId, final String tenantId) {
    return documents.findById(documentId)
      .filter(DocumentEntity::isActive)
      .filter(document -> document.getTenantId().equals(tenantId));
  }

}
