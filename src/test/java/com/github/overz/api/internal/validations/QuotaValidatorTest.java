package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.QuotaExceededException;
import com.github.overz.rag.AcceptedUpload;
import com.github.overz.rag.DocumentCommandApi;
import com.github.overz.rag.RegisteredDocument;
import com.github.overz.rag.TenantQuota;
import com.github.overz.rag.TenantUsage;
import com.github.overz.shared.security.CallerContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

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
    };
  }

}
