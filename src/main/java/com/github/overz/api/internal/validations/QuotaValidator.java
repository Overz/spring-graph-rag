package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.QuotaExceededException;
import com.github.overz.rag.DocumentCommandApi;
import lombok.RequiredArgsConstructor;

/**
 * Validação nº 7 (RF03 complemento): cota do tenant é opt-in (D4) — sem linha em
 * {@code tenant_quotas}, sem limite. Uso corrente derivado dos documentos ativos.
 */
@RequiredArgsConstructor
public final class QuotaValidator implements UploadValidator {

  private final DocumentCommandApi documents;

  @Override
  public void validate(final UploadCandidate candidate) {
    final var tenantId = candidate.caller().tenantId();
    documents.quotaOf(tenantId).ifPresent(quota -> {
      final var usage = documents.usageOf(tenantId);
      if (usage.storageBytes() + candidate.sizeBytes() > quota.maxStorageBytes()) {
        throw QuotaExceededException.storage();
      }
      if (usage.activeFiles() + 1 > quota.maxActiveFiles()) {
        throw QuotaExceededException.activeFiles();
      }
    });
  }

}
