package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF02: nome vazio, longo demais, com caracteres de controle ou path traversal. */
public final class InvalidFilenameException extends UploadRejectedException {

  public static final String CODE = "INVALID_FILENAME";

  public InvalidFilenameException(final String reason) {
    super(CODE, "Nome de arquivo inválido: " + reason, HttpStatus.BAD_REQUEST);
  }

}
