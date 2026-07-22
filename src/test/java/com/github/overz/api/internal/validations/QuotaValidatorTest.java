package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.QuotaExceededException;
import com.github.overz.rag.AcceptedUpload;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.DocumentCommandOutcome;
import com.github.overz.rag.DocumentHistoryEntry;
import com.github.overz.rag.DocumentStatus;
import com.github.overz.rag.DocumentSummary;
import com.github.overz.rag.RegisteredDocument;
import com.github.overz.rag.TenantQuota;
import com.github.overz.rag.TenantUsage;
import com.github.overz.rag.VersionReplacementResult;
import com.github.overz.shared.security.CallerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class QuotaValidatorTest extends Assertions {

  private final CallerContext caller = new CallerContext("tenant-1", "owner-1", Set.of());

  @Test
  void rejeitaQuandoCotaDeArmazenamentoExcedida() {
    final var validator = new QuotaValidator(documentCommandApi(new TenantQuota(100, 10), new TenantUsage(95, 1)));

    assertThrows(QuotaExceededException.class, () ->
      validator.validate(UploadCandidate.builder().caller(caller).sizeBytes(10).build()));
  }

  @Test
  void aceitaQuandoDentroDaCota() {
    final var validator = new QuotaValidator(documentCommandApi(new TenantQuota(100, 10), new TenantUsage(50, 1)));

    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().caller(caller).sizeBytes(10).build()));
  }

  @Test
  void aceitaQuandoTenantSemCotaConfigurada() {
    final var validator = new QuotaValidator(documentCommandApi(null, null));

    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().caller(caller).sizeBytes(Long.MAX_VALUE).build()));
  }

  private DocumentCommandApi documentCommandApi(final TenantQuota quota, final TenantUsage usage) {
    return new DocumentCommandApi() {
      @Override
      public RegisteredDocument registerAcceptedUpload(final AcceptedUpload upload) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean successfulDuplicateExists(final String tenantId, final String ownerId, final String sha256) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<TenantQuota> quotaOf(final String tenantId) {
        return Optional.ofNullable(quota);
      }

      @Override
      public TenantUsage usageOf(final String tenantId) {
        return usage;
      }

      @Override
      public Optional<DocumentStatus> statusOf(final UUID documentId, final String tenantId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<List<DocumentHistoryEntry>> historyOf(final UUID documentId, final String tenantId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Page<DocumentSummary> listDocuments(
        final String tenantId, final boolean includeInactive, final Pageable pageable
      ) {
        throw new UnsupportedOperationException();
      }

      @Override
      public DocumentCommandOutcome deleteDocument(final UUID documentId, final String tenantId, final String ownerId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public VersionReplacementResult replaceVersion(final UUID previousDocumentId, final AcceptedUpload newUpload) {
        throw new UnsupportedOperationException();
      }
    };
  }

}
