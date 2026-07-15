package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.DuplicateFileException;
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

class DuplicateFileValidatorTest extends Assertions {

  private final CallerContext caller = new CallerContext("tenant-1", "owner-1", Set.of());

  @Test
  void rejeitaHashJaExistente() {
    final var validator = new DuplicateFileValidator(documentCommandApi(true));

    assertThrows(DuplicateFileException.class, () ->
      validator.validate(UploadCandidate.builder().caller(caller).sha256("abc").build()));
  }

  @Test
  void aceitaHashInedito() {
    final var validator = new DuplicateFileValidator(documentCommandApi(false));

    assertDoesNotThrow(() ->
      validator.validate(UploadCandidate.builder().caller(caller).sha256("abc").build()));
  }

  private DocumentCommandApi documentCommandApi(final boolean duplicateExists) {
    return new DocumentCommandApi() {
      @Override
      public RegisteredDocument registerAcceptedUpload(final AcceptedUpload upload) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean successfulDuplicateExists(final String tenantId, final String ownerId, final String sha256) {
        return duplicateExists;
      }

      @Override
      public Optional<TenantQuota> quotaOf(final String tenantId) {
        throw new UnsupportedOperationException();
      }

      @Override
      public TenantUsage usageOf(final String tenantId) {
        throw new UnsupportedOperationException();
      }
    };
  }

}
