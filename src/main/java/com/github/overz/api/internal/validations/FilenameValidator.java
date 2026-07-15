package com.github.overz.api.internal.validations;

import com.github.overz.api.internal.configs.UploadProperties;
import com.github.overz.api.internal.dtos.UploadCandidate;
import com.github.overz.api.internal.errors.InvalidFilenameException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/** Validação nº 2 (RF02): nome vazio, longo demais, caracteres de controle, path traversal. */
@RequiredArgsConstructor
public final class FilenameValidator implements UploadValidator {

  private final UploadProperties properties;

  @Override
  public void validate(final UploadCandidate candidate) {
    final var filename = candidate.filename();
    if (StringUtils.isBlank(filename)) {
      throw new InvalidFilenameException("nome vazio");
    }
    if (filename.length() > properties.maxFilenameLength()) {
      throw new InvalidFilenameException("nome excede " + properties.maxFilenameLength() + " caracteres");
    }
    if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
      throw new InvalidFilenameException("caracteres de caminho não são permitidos");
    }
    if (filename.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
      throw new InvalidFilenameException("caracteres de controle não são permitidos");
    }
  }

}
