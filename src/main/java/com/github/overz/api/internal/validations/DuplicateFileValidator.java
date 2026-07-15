package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.DuplicateFileException;
import com.github.overz.rag.DocumentCommandApi;
import lombok.RequiredArgsConstructor;

/**
 * Validação nº 6 (RF07): mesmo hash SHA-256 do mesmo tenant+owner com envio anterior cujo
 * status não é {@code FAILED} bloqueia; o mesmo conteúdo de outro usuário não bloqueia.
 * A constraint {@code UNIQUE (tenant, owner, hash, version)} é o guarda final da corrida
 * entre uploads simultâneos.
 */
@RequiredArgsConstructor
public final class DuplicateFileValidator implements UploadValidator {

  private final DocumentCommandApi documents;

  @Override
  public void validate(final UploadCandidate candidate) {
    final var caller = candidate.caller();
    if (documents.successfulDuplicateExists(caller.tenantId(), caller.ownerId(), candidate.sha256())) {
      throw new DuplicateFileException();
    }
  }

}
