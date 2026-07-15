package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.EmptyFileException;

/** Validação nº 3 (RF02): arquivo de 0 bytes. */
public final class EmptyFileValidator implements UploadValidator {

  @Override
  public void validate(final UploadCandidate candidate) {
    if (candidate.sizeBytes() == 0) {
      throw new EmptyFileException();
    }
  }

}
