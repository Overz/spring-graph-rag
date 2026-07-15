package com.github.overz.api.internal.errors;

import org.springframework.http.HttpStatus;

/** RF02: arquivo de 0 bytes. */
public final class EmptyFileException extends UploadRejectedException {

  public static final String CODE = "EMPTY_FILE";

  public EmptyFileException() {
    super(CODE, "Arquivo vazio", HttpStatus.BAD_REQUEST);
  }

}
