package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.configs.UploadProperties;
import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.FileTooLargeException;
import lombok.RequiredArgsConstructor;

/** Validação nº 1 (RF03): limite de tamanho por arquivo individual ({@code app.upload.max-file-size-bytes}). */
@RequiredArgsConstructor
public final class FileSizeValidator implements UploadValidator {

  private final UploadProperties properties;

  @Override
  public void validate(final UploadCandidate candidate) {
    if (candidate.sizeBytes() > properties.maxFileSizeBytes()) {
      throw new FileTooLargeException();
    }
  }

}
