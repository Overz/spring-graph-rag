package com.github.overz.rag.internal.services;

import com.github.overz.rag.AcceptedUpload;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.DocumentStatus;
import com.github.overz.rag.RegisteredDocument;
import com.github.overz.rag.TenantQuota;
import com.github.overz.rag.TenantUsage;
import com.github.overz.rag.internal.models.DocumentEntity;
import com.github.overz.rag.internal.models.DocumentStatusHistoryEntity;
import com.github.overz.rag.internal.repositories.DocumentRepository;
import com.github.overz.rag.internal.repositories.DocumentStatusHistoryRepository;
import com.github.overz.rag.internal.repositories.TenantQuotaRepository;
import com.github.overz.shared.logging.ILogger;
import com.github.overz.shared.logging.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Fatia mínima do futuro {@code DocumentLifecycleService} (Épico 2): registra o aceite
 * do upload com o histórico {@code RECEIVED → VALIDATING → UPLOADED} numa transação
 * (D6 — os estados intermediários existem dentro da requisição síncrona, SDD ingestao §2).
 *
 * <p>Registrado como bean em {@code RagConfig} — sem estereótipo de classe.
 */
@RequiredArgsConstructor
public class DocumentIngestService implements DocumentCommandApi {

  private static final ILogger log = LoggerFactory.of(DocumentIngestService.class);

  private final DocumentRepository documents;
  private final DocumentStatusHistoryRepository history;
  private final TenantQuotaRepository quotas;

  @Override
  @Transactional
  public RegisteredDocument registerAcceptedUpload(final AcceptedUpload upload) {
    final var now = OffsetDateTime.now();
    // Inédito → 1; reenvio legítimo do mesmo conteúdo (anterior FAILED — a validação de
    // duplicidade já barrou os demais casos) → versão seguinte, preservando a linha falhada.
    final var version = documents.maxVersionOf(upload.tenantId(), upload.ownerId(), upload.sha256()) + 1;

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

  @Override
  @Transactional(readOnly = true)
  public boolean successfulDuplicateExists(final String tenantId, final String ownerId, final String sha256) {
    return documents.existsByTenantIdAndOwnerIdAndFileHashSha256AndStatusNot(
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

}
